const baseUrl = process.env.AMAP_BASE_URL || 'https://restapi.amap.com'
const key = process.env.AMAP_API_KEY || process.env.app_amap_api_key || process.env.APP_AMAP_API_KEY
if (!key) {
  console.error('Missing backend AMAP_API_KEY. Route order probing must use the backend WebService key, not the frontend JSAPI key.')
  process.exit(1)
}

const wait = (ms) => new Promise(resolve => setTimeout(resolve, ms))

async function amapGet(endpoint, params) {
  const url = new URL(endpoint, baseUrl)
  url.searchParams.set('key', key)
  for (const [name, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(name, String(value))
  }
  for (let attempt = 0; attempt < 5; attempt++) {
    const response = await fetch(url)
    const json = await response.json()
    if (json.status === '1') return json
    const info = `${json.info || 'unknown'} ${json.infocode || ''}`.trim()
    if (/CUQPS|QPS|DAILY_QUERY_OVER_LIMIT|10021/.test(info) && attempt < 4) {
      await wait(1200 * (attempt + 1))
      continue
    }
    throw new Error(`${endpoint} failed: ${info}`)
  }
  throw new Error(`${endpoint} failed`)
}

async function searchPoi(name, city = '成都') {
  const json = await amapGet('/v3/place/text', {
    keywords: name,
    city,
    citylimit: true,
    offset: 1,
    page: 1,
    extensions: 'base',
  })
  const poi = json.pois?.[0]
  if (!poi?.location) throw new Error(`No POI found: ${name}`)
  const [lng, lat] = poi.location.split(',').map(Number)
  return {
    name,
    amapName: poi.name,
    address: Array.isArray(poi.address) ? poi.address.join('') : poi.address,
    location: poi.location,
    lng,
    lat,
  }
}

async function distance(origin, destination) {
  const json = await amapGet('/v3/distance', {
    origins: origin.location,
    destination: destination.location,
    type: 1,
  })
  const result = json.results?.[0]
  if (!result) throw new Error(`No distance: ${origin.name} -> ${destination.name}`)
  return {
    meters: Number(result.distance || 0),
    seconds: Number(result.duration || 0),
  }
}

async function distancesToDestination(origins, destination) {
  const json = await amapGet('/v3/distance', {
    origins: origins.map(origin => origin.location).join('|'),
    destination: destination.location,
    type: 1,
  })
  const results = json.results || []
  if (results.length !== origins.length) {
    throw new Error(`Distance matrix mismatch for destination ${destination.name}: expected ${origins.length}, got ${results.length}`)
  }
  return origins.map((origin, index) => ({
    origin,
    destination,
    meters: Number(results[index].distance || 0),
    seconds: Number(results[index].duration || 0),
  }))
}

async function drivingRoute(sequence) {
  const origin = sequence[0]
  const destination = sequence[sequence.length - 1]
  const waypoints = sequence.slice(1, -1).map(point => point.location).join(';')
  const json = await amapGet('/v3/direction/driving', {
    origin: origin.location,
    destination: destination.location,
    waypoints,
    strategy: 0,
    extensions: 'base',
  })
  const pathResult = json.route?.paths?.[0]
  if (!pathResult) throw new Error(`No driving route: ${sequence.map(point => point.name).join(' -> ')}`)
  return {
    meters: Number(pathResult.distance || 0),
    seconds: Number(pathResult.duration || 0),
  }
}

function permutations(items) {
  if (items.length <= 1) return [items]
  const result = []
  for (let index = 0; index < items.length; index++) {
    const head = items[index]
    const rest = [...items.slice(0, index), ...items.slice(index + 1)]
    for (const tail of permutations(rest)) result.push([head, ...tail])
  }
  return result
}

function sequenceCost(sequence, matrix) {
  let meters = 0
  let seconds = 0
  for (let index = 0; index < sequence.length - 1; index++) {
    const leg = matrix.get(`${sequence[index].name}->${sequence[index + 1].name}`)
    meters += leg.meters
    seconds += leg.seconds
  }
  return { meters, seconds }
}

function formatMetric(metric) {
  return `${(metric.meters / 1000).toFixed(1)} km / ${Math.round(metric.seconds / 60)} min`
}

function names(sequence) {
  return sequence.map(point => point.name).join(' -> ')
}

async function runCase(config) {
  console.log(`\n=== ${config.name} ===`)
  const points = {}
  for (const name of [config.start, ...config.middle, config.end]) {
    points[name] = await searchPoi(name, config.city)
    await wait(120)
  }
  console.log('POI:')
  for (const point of Object.values(points)) {
    console.log(`- ${point.name}: ${point.amapName} / ${point.location}`)
  }

  const all = [points[config.start], ...config.middle.map(name => points[name]), points[config.end]]
  const matrix = new Map()
  for (const destination of all) {
    const origins = all.filter(point => point.name !== destination.name)
    const legs = await distancesToDestination(origins, destination)
    for (const leg of legs) {
      matrix.set(`${leg.origin.name}->${leg.destination.name}`, {
        meters: leg.meters,
        seconds: leg.seconds,
      })
    }
    await wait(900)
  }

  const start = points[config.start]
  const end = points[config.end]
  const candidates = permutations(config.middle.map(name => points[name])).map(middle => {
    const sequence = [start, ...middle, end]
    return { sequence, matrixCost: sequenceCost(sequence, matrix) }
  }).sort((left, right) => left.matrixCost.seconds - right.matrixCost.seconds)

  const original = [start, ...config.middle.map(name => points[name]), end]
  const optimized = candidates[0].sequence
  const worst = candidates[candidates.length - 1].sequence

  const originalRoute = await drivingRoute(original)
  await wait(120)
  const optimizedRoute = await drivingRoute(optimized)
  await wait(120)
  const worstRoute = await drivingRoute(worst)

  console.log('\nMatrix best order:')
  console.log(names(optimized))
  console.log(`matrix=${formatMetric(candidates[0].matrixCost)}, driving=${formatMetric(optimizedRoute)}`)

  console.log('\nOriginal order:')
  console.log(names(original))
  console.log(`matrix=${formatMetric(sequenceCost(original, matrix))}, driving=${formatMetric(originalRoute)}`)

  console.log('\nWorst matrix order:')
  console.log(names(worst))
  console.log(`matrix=${formatMetric(candidates[candidates.length - 1].matrixCost)}, driving=${formatMetric(worstRoute)}`)

  const savedKm = (originalRoute.meters - optimizedRoute.meters) / 1000
  const savedMin = (originalRoute.seconds - optimizedRoute.seconds) / 60
  console.log(`\nSaved vs original: ${savedKm.toFixed(1)} km / ${Math.round(savedMin)} min`)
}

await runCase({
  name: '成都城区 4 点：固定起终点，验证高德是否按传入顺序算途经点',
  city: '成都',
  start: '成都东站',
  middle: ['杜甫草堂', '成都博物馆', '武侯祠', '宽窄巷子'],
  end: '太古里',
})

await runCase({
  name: '成都周边 4 点：验证跨区域绕路风险',
  city: '成都',
  start: '成都东站',
  middle: ['都江堰景区', '彭镇古镇', '青城山景区', '成都博物馆'],
  end: '宽窄巷子',
})
