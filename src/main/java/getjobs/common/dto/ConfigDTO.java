package getjobs.common.dto;

import getjobs.repository.entity.ConfigEntity;
import getjobs.repository.ConfigRepository;
import getjobs.utils.SpringContextUtil;
import getjobs.common.enums.RecruitmentPlatformEnum;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class ConfigDTO {

    // 文本与布尔
    private String sayHi;

    // 逗号分隔原始输入（来自表单/配置）
    private String keywords;
    private String cityCode;
    private String industry;

    // 单/多选原始字符串（来自表单/配置）
    private String experience;
    private String jobType;
    private String salary;
    private String degree;
    private String scale;
    private String stage;
    private String companyType;  // 新增：公司类型字段，用于51job的companyType参数
    private String expectedPosition;

    // 可选：自定义城市编码映射
    private Map<String, String> customCityCode;

    // 功能开关与AI
    private Boolean enableAIJobMatchDetection;
    private Boolean enableAIGreeting;
    private Boolean filterDeadHR;
    private Boolean sendImgResume;
    private Boolean keyFilter;
    private Boolean recommendJobs;
    private Boolean checkStateOwned;

    // 简历配置
    private String resumeImagePath;
    private String resumeContent;

    // 期望薪资（min/max）
    private Integer minSalary;
    private Integer maxSalary;

    // 系统
    private String waitTime;

    // 平台类型
    private String platformType;

    // 其他列表型配置
    private List<String> deadStatus;

    // ------------ 单例加载 ------------
    private static volatile ConfigDTO instance;

    private ConfigDTO() {
    }

    public static ConfigDTO getInstance() {
        if (instance == null) {
            synchronized (ConfigDTO.class) {
                if (instance == null) {
                    instance = init();
                }
            }
        }
        return instance;
    }

    @SneakyThrows
    private static ConfigDTO init() {
        // 从数据库查询ConfigEntity
        ConfigRepository configRepository = SpringContextUtil.getBean(ConfigRepository.class);
        Optional<ConfigEntity> configOpt = configRepository.getDefaultConfig();

        if (configOpt.isEmpty()) {
            // 如果数据库中没有配置，返回默认配置
            return new ConfigDTO();
        }

        // 转换为BossConfigDTO
        return convertFromEntity(configOpt.get());
    }

    @SneakyThrows
    public static synchronized void reload() {
        instance = init();
    }

    /**
     * 将ConfigEntity转换为BossConfigDTO
     */
    private static ConfigDTO convertFromEntity(ConfigEntity entity) {
        ConfigDTO dto = new ConfigDTO();

        // 基础字段映射
        dto.setSayHi(entity.getSayHi());
        dto.setEnableAIJobMatchDetection(entity.getEnableAIJobMatchDetection());
        dto.setEnableAIGreeting(entity.getEnableAIGreeting());
        dto.setFilterDeadHR(entity.getFilterDeadHR());
        dto.setSendImgResume(entity.getSendImgResume());
        dto.setKeyFilter(entity.getKeyFilter());
        dto.setRecommendJobs(entity.getRecommendJobs());
        dto.setCheckStateOwned(entity.getCheckStateOwned());
        dto.setResumeImagePath(entity.getResumeImagePath());
        dto.setResumeContent(entity.getResumeContent());
        dto.setWaitTime(entity.getWaitTime());
        dto.setPlatformType(entity.getPlatformType());

        // 列表字段转换为逗号分隔的字符串
        if (entity.getKeywords() != null) {
            dto.setKeywords(String.join(",", entity.getKeywords()));
        }
        if (entity.getCityCode() != null) {
            dto.setCityCode(String.join(",", entity.getCityCode()));
        }
        if (entity.getIndustry() != null) {
            dto.setIndustry(String.join(",", entity.getIndustry()));
        }
        if (entity.getExperience() != null) {
            dto.setExperience(String.join(",", entity.getExperience()));
        }
        if (entity.getDegree() != null) {
            dto.setDegree(String.join(",", entity.getDegree()));
        }
        if (entity.getScale() != null) {
            dto.setScale(String.join(",", entity.getScale()));
        }
        if (entity.getStage() != null) {
            dto.setStage(String.join(",", entity.getStage()));
        }
        // 注意：需要在ConfigEntity中添加companyType字段
        // if (entity.getCompanyType() != null) {
        //     dto.setCompanyType(String.join(",", entity.getCompanyType()));
        // }
        if (entity.getDeadStatus() != null) {
            dto.setDeadStatus(entity.getDeadStatus());
        }

        // 期望薪资处理
        if (entity.getExpectedSalary() != null && entity.getExpectedSalary().size() >= 2) {
            dto.setMinSalary(entity.getExpectedSalary().get(0));
            dto.setMaxSalary(entity.getExpectedSalary().get(1));
        }

        // 其他字段
        dto.setCustomCityCode(entity.getCustomCityCode());
        dto.setJobType(entity.getJobType());
        dto.setSalary(entity.getSalary());
        dto.setExpectedPosition(entity.getExpectedPosition());

        return dto;
    }

    // ------------ 包装/转换访问器（供业务方使用）------------

    public List<String> getKeywordsList() {
        return splitToList(keywords);
    }

    public List<String> getCityCodeCodes() {
        List<String> cities = splitToList(cityCode);
        if (cities == null)
            return Collections.emptyList();
        return cities.stream()
                .map(city -> {
                    if (customCityCode != null && customCityCode.containsKey(city)) {
                        return customCityCode.get(city);
                    }
                    return city;
                })
                .collect(Collectors.toList());
    }

    public List<String> getIndustryCodes() {
        return mapToCodes(splitToList(industry), v -> v);
    }

    public List<String> getExperienceCodes() {
        return mapToCodes(splitToList(experience), v -> v);
    }


    public List<String> getDegreeCodes() {
        return mapToCodes(splitToList(degree), v -> degree);
    }

    public List<String> getScaleCodes() {
        return mapToCodes(splitToList(scale), v -> scale);
    }

    public List<String> getStageCodes() {
        return mapToCodes(splitToList(stage), v -> stage);
    }

    public List<String> getCompanyTypeCodes() {
        return mapToCodes(splitToList(companyType), v -> companyType);
    }

    public List<Integer> getExpectedSalary() {
        List<Integer> list = new ArrayList<>();
        if (minSalary != null)
            list.add(minSalary);
        if (maxSalary != null)
            list.add(maxSalary);
        return list;
    }

    /**
     * 获取平台类型对应的枚举
     * @return 招聘平台枚举，如果platformType为空或无效则返回null
     */
    public RecruitmentPlatformEnum getPlatformTypeEnum() {
        if (platformType == null || platformType.trim().isEmpty()) {
            return null;
        }
        return RecruitmentPlatformEnum.getByCode(platformType.trim());
    }

    // ------------ 工具方法 ------------
    private List<String> splitToList(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        // 支持中文/英文逗号、竖线、空格分隔
        String[] arr = text.split("[，,|\\s]+");
        return Arrays.stream(arr)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> mapToCodes(List<String> src, java.util.function.Function<String, String> mapper) {
        if (src == null)
            return new ArrayList<>();
        return src.stream().map(mapper).collect(Collectors.toList());
    }
}
