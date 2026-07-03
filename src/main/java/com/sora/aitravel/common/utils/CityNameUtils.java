package com.sora.aitravel.common.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Normalizes user-entered places into city names for route and rental matching. */
public final class CityNameUtils {

    private static final List<String> KNOWN_CITY_NAMES =
            List.of(
                            "乌鲁木齐", "呼和浩特", "哈尔滨", "石家庄", "秦皇岛", "张家口", "连云港", "景德镇", "张家界", "西双版纳",
                            "香格里拉", "齐齐哈尔", "佳木斯", "牡丹江", "满洲里", "鄂尔多斯", "巴彦淖尔", "锡林郭勒", "阿拉善",
                            "阿勒泰", "吐鲁番", "喀什", "伊犁", "克拉玛依", "石河子", "北京", "上海", "天津", "重庆", "成都",
                            "杭州", "广州", "深圳", "南京", "苏州", "无锡", "常州", "扬州", "镇江", "南通", "泰州", "徐州",
                            "盐城", "淮安", "宿迁", "宁波", "温州", "绍兴", "嘉兴", "湖州", "金华", "台州", "舟山", "丽水",
                            "衢州", "合肥", "芜湖", "黄山", "安庆", "池州", "宣城", "滁州", "马鞍山", "蚌埠", "淮南", "淮北",
                            "阜阳", "宿州", "六安", "亳州", "福州", "厦门", "泉州", "漳州", "莆田", "三明", "南平", "龙岩",
                            "宁德", "南昌", "九江", "赣州", "上饶", "吉安", "宜春", "抚州", "萍乡", "新余", "鹰潭", "济南",
                            "青岛", "烟台", "威海", "潍坊", "淄博", "泰安", "济宁", "临沂", "日照", "枣庄", "东营", "德州",
                            "聊城", "滨州", "菏泽", "郑州", "洛阳", "开封", "安阳", "新乡", "焦作", "许昌", "平顶山", "南阳",
                            "信阳", "商丘", "周口", "驻马店", "濮阳", "漯河", "三门峡", "鹤壁", "武汉", "宜昌", "襄阳",
                            "十堰", "荆州", "荆门", "黄石", "黄冈", "孝感", "咸宁", "随州", "恩施", "长沙", "株洲", "湘潭",
                            "衡阳", "岳阳", "常德", "益阳", "郴州", "永州", "怀化", "娄底", "邵阳", "湘西", "韶关", "珠海",
                            "佛山", "江门", "湛江", "茂名", "肇庆", "惠州", "梅州", "汕头", "汕尾", "河源", "阳江", "清远",
                            "东莞", "中山", "潮州", "揭阳", "云浮", "南宁", "桂林", "柳州", "北海", "梧州", "防城港", "钦州",
                            "贵港", "玉林", "百色", "贺州", "河池", "来宾", "崇左", "海口", "三亚", "儋州", "琼海", "万宁",
                            "文昌", "昆明", "大理", "丽江", "迪庆", "普洱", "保山", "昭通", "曲靖", "玉溪", "楚雄", "红河",
                            "文山", "临沧", "德宏", "怒江", "贵阳", "遵义", "安顺", "六盘水", "毕节", "铜仁", "黔东南",
                            "黔南", "黔西南", "拉萨", "林芝", "日喀则", "山南", "那曲", "昌都", "阿里", "西安", "宝鸡",
                            "咸阳", "铜川", "渭南", "延安", "汉中", "榆林", "安康", "商洛", "兰州", "嘉峪关", "金昌", "白银",
                            "天水", "武威", "张掖", "平凉", "酒泉", "庆阳", "定西", "陇南", "临夏", "甘南", "西宁", "海东",
                            "海北", "黄南", "果洛", "玉树", "海西", "银川", "石嘴山", "吴忠", "固原", "中卫", "太原", "大同",
                            "阳泉", "长治", "晋城", "朔州", "晋中", "运城", "忻州", "临汾", "吕梁", "沈阳", "大连", "鞍山",
                            "抚顺", "本溪", "丹东", "锦州", "营口", "阜新", "辽阳", "盘锦", "铁岭", "朝阳", "葫芦岛", "长春",
                            "吉林", "四平", "辽源", "通化", "白山", "松原", "白城", "延边")
                    .stream()
                    .distinct()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .toList();

    private CityNameUtils() {}

    public static String normalizeCity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text =
                value.trim()
                        .replaceAll(
                                "^(四川|云南|贵州|西藏|陕西|甘肃|青海|宁夏|新疆|山西|河北|河南|山东|辽宁|吉林|黑龙江|江苏|浙江|安徽|福建|江西|湖北|湖南|广东|广西|海南|内蒙古)",
                                "")
                        .replaceAll("(省|自治区|壮族自治区|回族自治区|维吾尔自治区|特别行政区)", "")
                        .replaceAll("(市|地区)$", "")
                        .replaceAll("(国际机场|机场|高铁站|火车站|动车站|客运站|汽车站|东站|西站|南站|北站|站)$", "")
                        .replaceAll("(?<=[\\u4e00-\\u9fa5]{2})(东|西|南|北)$", "")
                        .trim();
        if (text.isBlank()) {
            return null;
        }
        String direct = firstKnownCity(text);
        if (direct != null) {
            return direct;
        }
        return text;
    }

    public static List<String> normalizeCityList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String city = normalizeCity(value);
            if (city != null && !result.contains(city)) {
                result.add(city);
            }
        }
        return result;
    }

    public static String firstNonBlankCity(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String city = normalizeCity(value);
            if (city != null) {
                return city;
            }
        }
        return null;
    }

    public static boolean sameCity(String left, String right) {
        return Objects.equals(normalizeCity(left), normalizeCity(right));
    }

    private static String firstKnownCity(String text) {
        for (String city : KNOWN_CITY_NAMES) {
            if (text.equals(city) || text.startsWith(city)) {
                return city;
            }
        }
        for (String city : KNOWN_CITY_NAMES) {
            if (text.contains(city)) {
                return city;
            }
        }
        return null;
    }
}
