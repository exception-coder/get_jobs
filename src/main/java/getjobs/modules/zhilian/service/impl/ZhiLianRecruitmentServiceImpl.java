package getjobs.modules.zhilian.service.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import getjobs.common.dto.ConfigDTO;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.modules.zhilian.service.ZhiLianElementLocators;
import getjobs.service.RecruitmentService;
import getjobs.utils.PlaywrightUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智联招聘服务实现类
 * 
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Service
public class ZhiLianRecruitmentServiceImpl implements RecruitmentService {

    private static final String HOME_URL = RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN.getHomeUrl();
    private static final String SEARCH_JOB_URL = "https://www.zhaopin.com/sou?";
    // https://www.zhaopin.com/sou?el=4&we=0510&et=2&sl=15001,25000&jl=763&kw=java
    @Override
    public RecruitmentPlatformEnum getPlatform() {
        return RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN;
    }

    @Override
    public boolean login(ConfigDTO config) {
        log.info("开始智联招聘登录检查");
        
        try {
            // 使用Playwright打开网站
            Page page = PlaywrightUtil.getPageObject();
            page.navigate(HOME_URL);
            
            // 检查是否需要登录
            if (ZhiLianElementLocators.isLoginRequired(page)) {
                log.info("需要登录，开始登录流程");
                return performLogin();
            } else {
                log.info("智联招聘已登录");
                return true;
            }
        } catch (Exception e) {
            log.error("智联招聘登录失败", e);
            return false;
        }
    }

    @Override
    public List<JobDTO> collectJobs(ConfigDTO config) {
        log.info("开始智联招聘岗位采集");
        List<JobDTO> allJobDTOS = new ArrayList<>();
        
        try {
            // 按城市和关键词搜索岗位
            for (String cityCode : config.getCityCodeCodes()) {
                for (String keyword : config.getKeywordsList()) {
                    List<JobDTO> jobDTOS = collectJobsByCity(cityCode, keyword, config);
                    allJobDTOS.addAll(jobDTOS);
                }
            }
            
            log.info("智联招聘岗位采集完成，共采集{}个岗位", allJobDTOS.size());
            return allJobDTOS;
        } catch (Exception e) {
            log.error("智联招聘岗位采集失败", e);
            return allJobDTOS;
        }
    }

    @Override
    public List<JobDTO> collectRecommendJobs(ConfigDTO config) {
        return List.of();
    }

    @Override
    public List<JobDTO> filterJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        return List.of();
    }

    @Override
    public int deliverJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        log.info("开始执行智联招聘岗位投递操作，待投递岗位数量: {}", jobDTOS.size());
        AtomicInteger successCount = new AtomicInteger(0);

        // 为投递任务创建一个独立的、隔离的浏览器上下文，防止与其他操作冲突
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent(PlaywrightUtil.getRandomUserAgent())
                .setJavaScriptEnabled(true)
                .setBypassCSP(true)
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai");

        try (BrowserContext deliveryContext = PlaywrightUtil.getBrowser().newContext(contextOptions);
             Page jobPage = deliveryContext.newPage()) {

            jobPage.setDefaultTimeout(30000); // 为新页面设置默认超时

            for (JobDTO jobDTO : jobDTOS) {
                try {
                    log.info("正在投递岗位: {}", jobDTO.getJobName());
                    jobPage.navigate(jobDTO.getHref());
                    jobPage.waitForLoadState(); // 等待页面加载


                    // 投递完成会打开新页签，需要关闭新页签
                    Page popup = jobPage.waitForPopup(() -> {
                        // 执行投递
                        if (ZhiLianElementLocators.clickSummaryApplyButton(jobPage)) {
                            log.info("岗位投递成功: {}", jobDTO.getJobName());
                            successCount.getAndIncrement();
                        } else {
                            log.warn("岗位投递失败或已投递: {}", jobDTO.getJobName());
                        }
                    });

                    if (popup != null) {
                        popup.waitForLoadState();       // 可选：等加载稳定
                        // TODO: 可根据 URL/标题做一次校验，确认是“投递成功”页
                        popup.close();                  // 关闭新页签
                    }


                    // 添加3-5秒随机延迟，避免投递过快
                    PlaywrightUtil.randomSleep(3, 5);

                } catch (Exception e) {
                    log.error("投递岗位 {} 时发生异常: {}", jobDTO.getJobName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("智联招聘岗位投递过程中发生严重错误", e);
        }

        log.info("智联招聘岗位投递完成，成功投递 {} 个岗位", successCount.get());
        return successCount.get();
    }

    @Override
    public boolean isDeliveryLimitReached() {
        return false;
    }

    @Override
    public void saveData(String dataPath) {
        log.info("开始保存智联招聘数据到路径: {}", dataPath);
        try {
            // TODO: 实现智联招聘数据保存逻辑
            // 这里需要保存智联招聘相关的数据，如登录状态、采集的岗位信息等
            
            log.info("智联招聘数据保存功能待实现");
            
        } catch (Exception e) {
            log.error("智联招聘数据保存失败", e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 按城市采集岗位
     */
    private List<JobDTO> collectJobsByCity(String cityCode, String keyword, ConfigDTO config) {
        String searchUrl = buildSearchUrl(cityCode, keyword, config);
        log.info("开始采集，城市: {}，关键词: {}，URL: {}", cityCode, keyword, searchUrl);
        
        List<JobDTO> jobDTOS = new ArrayList<>();
        Page page = PlaywrightUtil.getPageObject();
        
        try {
            page.navigate(searchUrl);
            // 等待页面加载
            page.waitForLoadState();
            int pageNumber = 1;
            while (ZhiLianElementLocators.clickPageNumber(page, pageNumber)){
                pageNumber++;
            }
        } catch (Exception e) {
            log.error("采集城市: {}, 关键词: {} 的职位失败", cityCode, keyword, e);
        }
        
        log.info("城市: {}, 关键词: {} 的职位采集完成，共{}个职位", cityCode, keyword, jobDTOS.size());
        return jobDTOS;
    }

    /**
     * 构建搜索URL
     * 基于URL: https://www.zhaopin.com/sou?el=4&we=0510&et=2&sl=15001,25000&jl=763&kw=java
     * 
     * @param cityCode 城市代码
     * @param keyword 关键词
     * @param config 配置信息
     * @return 完整的搜索URL
     */
    private String buildSearchUrl(String cityCode, String keyword, ConfigDTO config) {
        StringBuilder url = new StringBuilder(SEARCH_JOB_URL);
        
        try {
            // 必需参数
            // 关键词需要URL编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            url.append("kw=").append(encodedKeyword);
            
            // 城市参数 jl
            if (cityCode != null && !cityCode.trim().isEmpty()) {
                url.append("&jl=").append(cityCode);
            }
            
            // 学历要求 el (Education Level)
            // el=4 表示本科, 1=不限, 2=高中, 3=大专, 4=本科, 5=硕士, 6=博士
            if (config.getDegree() != null && !config.getDegree().trim().isEmpty()) {
                url.append("&el=").append(config.getDegree());
            }
            
            // 职位类型 et (Employment Type)  
            // et=2 表示全职, 1=全职, 2=兼职, 3=实习
            if (config.getJobType() != null && !config.getJobType().trim().isEmpty()) {
                url.append("&et=").append(config.getJobType());
            }
            
            // 公司性质 ct (Company Type)
            if (config.getCompanyType() != null && !config.getCompanyType().trim().isEmpty()) {
                url.append("&ct=").append(config.getCompanyType());
            }
            
            // 公司规模 cs (Company Size)
            if (config.getScale() != null && !config.getScale().trim().isEmpty()) {
                url.append("&cs=").append(config.getScale());
            }
            
            // 薪资范围 sl (Salary Level)
            // 格式: sl=15001,25000
            if (config.getSalary() != null && !config.getSalary().trim().isEmpty()) {
                url.append("&sl=").append(config.getSalary());
            }
            
            // 工作经验 we (Work Experience)
            // we=0510 表示5-10年
            if (config.getExperience() != null && !config.getExperience().trim().isEmpty()) {
                url.append("&we=").append(config.getExperience());
            }
            
            // 行业类型 in
            if (config.getIndustry() != null && !config.getIndustry().trim().isEmpty()) {
                url.append("&in=").append(URLEncoder.encode(config.getIndustry(), StandardCharsets.UTF_8));
            }
            
        } catch (Exception e) {
            log.error("构建搜索URL失败", e);
            // 返回基础URL
            return SEARCH_JOB_URL + "kw=" + keyword;
        }
        
        String finalUrl = url.toString();
        log.debug("构建的搜索URL: {}", finalUrl);
        return finalUrl;
    }

    /**
     * 执行登录操作
     */
    private boolean performLogin() {
        Page page = PlaywrightUtil.getPageObject();
        
        try {
            // 直接首页登录即可，不需要单独使用登录页
            page.navigate(HOME_URL);
            PlaywrightUtil.sleep(3);
            
            log.info("等待用户手动登录...");
            log.info("请在浏览器中完成登录操作");
            
            boolean loginSuccess = false;


            while (!loginSuccess) {
                try {
                    // 检查登录状态
                    if (ZhiLianElementLocators.isUserLoggedIn(page)) {
                        loginSuccess = true;
                        log.info("登录成功");
                    }
                } catch (Exception e) {
                    log.debug("登录状态检查异常: {}", e.getMessage());
                }
                
                PlaywrightUtil.sleep(2);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("登录过程中发生错误", e);
            return false;
        }
    }

    /**
     * 等待用户输入或超时
     */
    private boolean waitForUserInputOrTimeout(Scanner scanner) {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            try {
                if (System.in.available() > 0) {
                    scanner.nextLine();
                    return true;
                }
            } catch (IOException e) {
                // 忽略异常
            }
            PlaywrightUtil.sleep(1);
        }
        return false;
    }
}
