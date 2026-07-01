package com.sora.aitravel.service;

import java.util.List;

public interface RegionRoutePresetService {

    RegionPresetGroup matchGroup(String destination);

    RoutePreset selectPreset(RegionPresetGroup group, int days, List<String> preferences);

    class RegionPresetGroup {
        private String type;
        private List<String> aliases;
        private String defaultCity;
        private List<RoutePreset> presets;
        private String standardName;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases;
        }

        public String getDefaultCity() {
            return defaultCity;
        }

        public void setDefaultCity(String defaultCity) {
            this.defaultCity = defaultCity;
        }

        public List<RoutePreset> getPresets() {
            return presets;
        }

        public void setPresets(List<RoutePreset> presets) {
            this.presets = presets;
        }

        public String getStandardName() {
            return standardName;
        }

        public void setStandardName(String standardName) {
            this.standardName = standardName;
        }
    }

    class RoutePreset {
        private String name;
        private Integer minDays;
        private Integer maxDays;
        private List<String> cities;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getMinDays() {
            return minDays;
        }

        public void setMinDays(Integer minDays) {
            this.minDays = minDays;
        }

        public Integer getMaxDays() {
            return maxDays;
        }

        public void setMaxDays(Integer maxDays) {
            this.maxDays = maxDays;
        }

        public List<String> getCities() {
            return cities;
        }

        public void setCities(List<String> cities) {
            this.cities = cities;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
}
