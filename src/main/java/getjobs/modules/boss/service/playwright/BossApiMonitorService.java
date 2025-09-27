package getjobs.modules.boss.service.playwright;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.openjson.JSONObject;
import com.microsoft.playwright.*;
import getjobs.modules.boss.dto.BossApiResponse;
import getjobs.repository.entity.JobEntity;
import getjobs.repository.JobRepository;
import getjobs.utils.BossJobDataConverter;
import getjobs.utils.PlaywrightUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Boss接口监控服务
 * 负责监听和记录Boss直聘相关的API请求和响应
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossApiMonitorService {

    private final JobRepository jobRepository;
    private final BossJobDataConverter dataConverter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 全局接口调用频率限制：记录最后一次调用时间
    private static volatile long lastCallTime = 0L;

    // 接口调用间隔：10秒
    private static final long CALL_INTERVAL_MS = 10000L;

    // 全局锁，确保同一时间只有一个请求在执行
    private static final ReentrantLock globalLock = new ReentrantLock();

    /**
     * 初始化监控服务
     * 设置岗位搜索和推荐岗位接口监听器
     */
    @PostConstruct
    public void init() {
        setupJobApiMonitor();
    }

    /**
     * 设置岗位搜索和推荐岗位接口监听器
     * 监听所有相关的API响应并打印完整报文
     */
    public void setupJobApiMonitor() {
        try {
            BrowserContext ctx = PlaywrightUtil.getContext();
            Page page = PlaywrightUtil.getPageObject();

            // 监听岗位搜索接口
            // setupJobSearchMonitor(ctx);

            // 监听岗位推荐接口
            // setupRecommendJobMonitor(ctx);

            // 监听所有岗位相关接口的响应
            setupResponseMonitor(page);

            log.info("Boss API监控服务初始化完成");
        } catch (Exception e) {
            log.error("Boss API监控服务初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 设置岗位搜索接口监控
     */
    private void setupJobSearchMonitor(BrowserContext ctx) {
        ctx.route("**/wapi/zpgeek/search/joblist.json**", route -> {

            try {
                Request req = route.request();
                log.info("=== 岗位搜索请求拦截 ===");
                log.info("请求方法: {}", req.method());
                log.info("请求URL: {}", req.url());
                log.info("请求头: {}", req.headers());
                log.info("请求参数: {}", req.url());
                log.info("========================");
            } catch (Exception e) {
                log.error("route error", e);
            } finally {
                // 继续请求
                route.resume();
            }

        });
    }

    /**
     * 设置推荐岗位接口监控
     */
    private void setupRecommendJobMonitor(BrowserContext ctx) {
        ctx.route("**/wapi/zpgeek/pc/recommend/job/list.json**", route -> {
            try {
                Request req = route.request();
                String url = req.url();
                log.info("=== 推荐岗位请求拦截 ===");
                log.info("请求方法: {}", req.method());
                log.info("请求URL: {}", url);
                log.info("请求头: {}", req.headers());
                log.info("请求参数: {}", url);
                log.info("========================");
            } catch (Exception e) {
                log.error("route error", e);
            } finally {
                // 继续请求
                route.resume();
            }
        });
    }

    /**
     * 设置响应监控
     */
    private void setupResponseMonitor(Page page) {
        page.onResponse(res -> {
            String url = res.url();

            // 监听岗位搜索接口响应
            if (url.contains("/wapi/zpgeek/search/joblist.json")) {
                handleJobSearchResponse(res);
            }
            // 监听推荐岗位接口响应
            else if (url.contains("/wapi/zpgeek/pc/recommend/job/list.json")) {
                handleRecommendJobResponse(res);
            }
            else if(url.contains("/wapi/zpgeek/job/detail.json")){
                handleJobDetailResponse(res);
            }
        });
    }

    private void handleJobDetailResponse(Response res) {
        log.info("=== 岗位详情响应拦截 ===");
        log.info("响应状态: {}", res.status());
        log.info("响应URL: {}", res.url());
        log.info("响应头: {}", res.headers());

        try {
            String body = res.text();
            log.info("响应体长度: {} 字符", body.length());
            log.info("响应体内容: {}", body);

            JSONObject jsonResponse = new JSONObject(body);
            // 解析并保存职位数据
            parseAndUpdateJobDetail(jsonResponse);

        } catch (PlaywrightException e) {
            log.error("读取响应体失败: {}", e.getMessage());
        }
        log.info("==========================");

    }

    /**
     * 处理岗位搜索响应
     */
    private void handleJobSearchResponse(Response res) {
        log.info("=== 岗位搜索响应拦截 ===");
        log.info("响应状态: {}", res.status());
        log.info("响应URL: {}", res.url());
        log.info("响应头: {}", res.headers());

        try {
            String body = res.text();
            log.info("响应体长度: {} 字符", body.length());
            log.info("响应体内容: {}", body);

            // 尝试解析JSON并美化输出
            formatJsonResponse(body);

            // 解析并保存职位数据
            parseAndSaveJobData(body, "岗位搜索");

        } catch (PlaywrightException e) {
            log.error("读取响应体失败: {}", e.getMessage());
        }
        log.info("==========================");
    }

    /**
     * 处理推荐岗位响应
     */
    private void handleRecommendJobResponse(com.microsoft.playwright.Response res) {
        log.info("=== 推荐岗位响应拦截 ===");
        log.info("响应状态: {}", res.status());
        log.info("响应URL: {}", res.url());
        log.info("响应头: {}", res.headers());

        try {
            String body = res.text();
            log.info("响应体长度: {} 字符", body.length());
            log.info("响应体内容: {}", body);

            // 尝试解析JSON并美化输出
            formatJsonResponse(body);

            // 解析并保存职位数据
            parseAndSaveJobData(body, "推荐岗位");

        } catch (PlaywrightException e) {
            log.error("读取响应体失败: {}", e.getMessage());
        }
        log.info("==========================");
    }

    /**
     * 格式化JSON响应
     */
    private void formatJsonResponse(String body) {
        try {
            JSONObject jsonResponse = new JSONObject(body);
            log.debug("格式化JSON响应: {}", jsonResponse.toString(2));
        } catch (Exception e) {
            log.debug("响应体不是有效的JSON格式");
        }
    }

    /**
     * 解析并保存职位数据
     * 
     * @param body   响应体JSON字符串
     * @param source 数据来源描述
     */
    @Transactional
    public void parseAndSaveJobData(String body, String source) {
        try {
            // 解析BOSS直聘API响应
            BossApiResponse response = objectMapper.readValue(body, BossApiResponse.class);

            if (response.getCode() != 0) {
                log.warn("BOSS直聘API响应错误，code: {}, message: {}", response.getCode(), response.getMessage());
                return;
            }

            if (response.getZpData() == null || response.getZpData().getJobList() == null) {
                log.warn("BOSS直聘API响应中没有职位数据");
                return;
            }

            List<Map<String, Object>> jobList = response.getZpData().getJobList();
            log.info("从{}获取到 {} 个职位数据", source, jobList.size());

            // 转换为JobEntity并保存
            List<JobEntity> jobEntities = jobList.stream()
                    .map(dataConverter::convertToJobEntity)
                    .filter(entity -> entity != null)
                    .collect(Collectors.toList());

            if (!jobEntities.isEmpty()) {
                // 检查是否已存在相同的职位（基于encryptJobId）
                List<JobEntity> newJobs = jobEntities.stream()
                        .filter(entity -> !isJobExists(entity.getEncryptJobId()))
                        .collect(Collectors.toList());

                if (!newJobs.isEmpty()) {
                    jobRepository.saveAll(newJobs);
                    log.info("成功保存 {} 个新职位到数据库，来源: {}", newJobs.size(), source);
                } else {
                    log.info("所有职位都已存在，跳过保存，来源: {}", source);
                }
            } else {
                log.warn("没有有效的职位数据可以保存，来源: {}", source);
            }

        } catch (Exception e) {
            log.error("解析并保存职位数据失败，来源: {}", source, e);
        }
    }

    /**
     * 检查职位是否已存在
     * 
     * @param encryptJobId 加密职位ID
     * @return 是否存在
     */
    private boolean isJobExists(String encryptJobId) {
        if (encryptJobId == null || encryptJobId.trim().isEmpty()) {
            return false;
        }

        try {
            return jobRepository.existsByEncryptJobId(encryptJobId);
        } catch (Exception e) {
            log.warn("检查职位是否存在时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 手动启动监控（如果需要重新启动）
     */
    public void startMonitoring() {
        log.info("手动启动Boss API监控服务");
        setupJobApiMonitor();
    }

    /**
     * 检查监控服务状态
     */
    public boolean isMonitoringActive() {
        try {
            BrowserContext ctx = PlaywrightUtil.getContext();
            return ctx != null;
        } catch (Exception e) {
            log.warn("检查监控服务状态失败: {}", e.getMessage());
            return false;
        }
    }


    /**
     * 解析职位明细数据并更新到数据库
     *
     * @param jsonResponse 职位明细JSON响应
     */
    @Transactional
    protected void parseAndUpdateJobDetail(JSONObject jsonResponse) {
        String encryptId = jsonResponse.optJSONObject("zpData")
                .optJSONObject("jobInfo")
                .optString("encryptId");
        parseAndUpdateJobDetail(jsonResponse, encryptId);
    }

    /**
     * 解析职位明细数据并更新到数据库
     * 
     * @param jsonResponse 职位明细JSON响应
     * @param encryptJobId   加密JobId
     */
    @Transactional
    protected void parseAndUpdateJobDetail(JSONObject jsonResponse, String encryptJobId) {
        try {
            // 根据encryptJobId查找对应的JobEntity
            JobEntity jobEntity = jobRepository.findByEncryptJobId(encryptJobId);
            if (jobEntity == null) {
                log.warn("未找到encryptJobId为 {} 的职位记录", encryptJobId);
                return;
            }

            // 获取zpData节点
            JSONObject zpData = jsonResponse.optJSONObject("zpData");
            if (zpData == null) {
                log.warn("职位明细响应中没有zpData节点，encryptJobId: {}", encryptJobId);
                return;
            }

            // 解析jobInfo节点
            JSONObject jobInfo = zpData.optJSONObject("jobInfo");
            if (jobInfo != null) {
                updateJobEntityFromJobInfo(jobEntity, jobInfo);
            }

            // 解析bossInfo节点
            JSONObject bossInfo = zpData.optJSONObject("bossInfo");
            if (bossInfo != null) {
                updateJobEntityFromBossInfo(jobEntity, bossInfo);
            }

            // 解析brandComInfo节点
            JSONObject brandComInfo = zpData.optJSONObject("brandComInfo");
            if (brandComInfo != null) {
                updateJobEntityFromBrandComInfo(jobEntity, brandComInfo);
            }

            // 保存更新后的实体
            jobRepository.save(jobEntity);
            log.info("成功更新职位明细信息，encryptJobId: {}, 职位: {}", encryptJobId, jobEntity.getJobTitle());

        } catch (Exception e) {
            log.error("解析并更新职位明细数据失败，encryptJobId: {}", encryptJobId, e);
        }
    }

    /**
     * 从jobInfo节点更新JobEntity
     * 
     * @param jobEntity JobEntity实例
     * @param jobInfo   jobInfo JSON对象
     */
    private void updateJobEntityFromJobInfo(JobEntity jobEntity, JSONObject jobInfo) {
        try {
            // 基础职位信息
            if (jobInfo.has("encryptId") && !jobInfo.isNull("encryptId")) {
                jobEntity.setEncryptJobDetailId(jobInfo.getString("encryptId"));
            }
            if (jobInfo.has("encryptUserId") && !jobInfo.isNull("encryptUserId")) {
                jobEntity.setEncryptJobUserId(jobInfo.getString("encryptUserId"));
            }
            if (jobInfo.has("invalidStatus")) {
                jobEntity.setJobInvalidStatus(jobInfo.getBoolean("invalidStatus"));
            }
            if (jobInfo.has("jobName") && !jobInfo.isNull("jobName")) {
                jobEntity.setJobTitle(jobInfo.getString("jobName"));
            }
            if (jobInfo.has("position")) {
                jobEntity.setJobPositionCode(jobInfo.getLong("position"));
            }
            if (jobInfo.has("positionName") && !jobInfo.isNull("positionName")) {
                jobEntity.setJobPositionName(jobInfo.getString("positionName"));
            }
            if (jobInfo.has("location")) {
                jobEntity.setJobLocationCode(jobInfo.getLong("location"));
            }
            if (jobInfo.has("locationName") && !jobInfo.isNull("locationName")) {
                jobEntity.setJobLocationName(jobInfo.getString("locationName"));
            }
            if (jobInfo.has("locationUrl") && !jobInfo.isNull("locationUrl")) {
                jobEntity.setJobLocationUrl(jobInfo.getString("locationUrl"));
            }
            if (jobInfo.has("experienceName") && !jobInfo.isNull("experienceName")) {
                jobEntity.setJobExperienceName(jobInfo.getString("experienceName"));
            }
            if (jobInfo.has("degreeName") && !jobInfo.isNull("degreeName")) {
                jobEntity.setJobDegreeName(jobInfo.getString("degreeName"));
            }
            if (jobInfo.has("jobType")) {
                jobEntity.setJobDetailType(jobInfo.getInt("jobType"));
            }
            if (jobInfo.has("proxyJob")) {
                jobEntity.setJobProxyJob(jobInfo.getInt("proxyJob"));
            }
            if (jobInfo.has("proxyType")) {
                jobEntity.setJobProxyType(jobInfo.getInt("proxyType"));
            }
            if (jobInfo.has("salaryDesc") && !jobInfo.isNull("salaryDesc")) {
                jobEntity.setSalaryDesc(jobInfo.getString("salaryDesc"));
            }
            if (jobInfo.has("payTypeDesc") && !jobInfo.isNull("payTypeDesc")) {
                jobEntity.setJobPayTypeDesc(jobInfo.getString("payTypeDesc"));
            }
            if (jobInfo.has("postDescription") && !jobInfo.isNull("postDescription")) {
                jobEntity.setJobPostDescription(jobInfo.getString("postDescription"));
            }
            if (jobInfo.has("encryptAddressId") && !jobInfo.isNull("encryptAddressId")) {
                jobEntity.setEncryptAddressId(jobInfo.getString("encryptAddressId"));
            }
            if (jobInfo.has("address") && !jobInfo.isNull("address")) {
                jobEntity.setJobAddress(jobInfo.getString("address"));
            }
            if (jobInfo.has("longitude")) {
                jobEntity.setJobLongitude(new BigDecimal(jobInfo.getString("longitude")));
            }
            if (jobInfo.has("latitude")) {
                jobEntity.setJobLatitude(new BigDecimal(jobInfo.getString("latitude")));
            }
            if (jobInfo.has("staticMapUrl") && !jobInfo.isNull("staticMapUrl")) {
                jobEntity.setJobStaticMapUrl(jobInfo.getString("staticMapUrl"));
            }
            if (jobInfo.has("pcStaticMapUrl") && !jobInfo.isNull("pcStaticMapUrl")) {
                jobEntity.setJobPcStaticMapUrl(jobInfo.getString("pcStaticMapUrl"));
            }
            if (jobInfo.has("baiduStaticMapUrl") && !jobInfo.isNull("baiduStaticMapUrl")) {
                jobEntity.setJobBaiduStaticMapUrl(jobInfo.getString("baiduStaticMapUrl"));
            }
            if (jobInfo.has("baiduPcStaticMapUrl") && !jobInfo.isNull("baiduPcStaticMapUrl")) {
                jobEntity.setJobBaiduPcStaticMapUrl(jobInfo.getString("baiduPcStaticMapUrl"));
            }
            if (jobInfo.has("showSkills")) {
                jobEntity.setJobShowSkills(jobInfo.getJSONArray("showSkills").toString());
            }
            if (jobInfo.has("anonymous")) {
                jobEntity.setJobAnonymous(jobInfo.getInt("anonymous"));
            }
            if (jobInfo.has("jobStatusDesc") && !jobInfo.isNull("jobStatusDesc")) {
                jobEntity.setJobStatusDesc(jobInfo.getString("jobStatusDesc"));
            }

            log.debug("成功更新jobInfo信息到JobEntity");
        } catch (Exception e) {
            log.error("更新jobInfo信息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从bossInfo节点更新JobEntity
     * 
     * @param jobEntity JobEntity实例
     * @param bossInfo  bossInfo JSON对象
     */
    private void updateJobEntityFromBossInfo(JobEntity jobEntity, JSONObject bossInfo) {
        try {
            if (bossInfo.has("name") && !bossInfo.isNull("name")) {
                jobEntity.setBossName(bossInfo.getString("name"));
            }
            if (bossInfo.has("title") && !bossInfo.isNull("title")) {
                jobEntity.setBossTitle(bossInfo.getString("title"));
            }
            if (bossInfo.has("tiny") && !bossInfo.isNull("tiny")) {
                jobEntity.setBossTiny(bossInfo.getString("tiny"));
            }
            if (bossInfo.has("large") && !bossInfo.isNull("large")) {
                jobEntity.setBossLarge(bossInfo.getString("large"));
            }
            if (bossInfo.has("activeTimeDesc") && !bossInfo.isNull("activeTimeDesc")) {
                jobEntity.setBossActiveTimeDesc(bossInfo.getString("activeTimeDesc"));
            }
            if (bossInfo.has("bossOnline")) {
                jobEntity.setBossOnline(bossInfo.getBoolean("bossOnline"));
            }
            if (bossInfo.has("brandName") && !bossInfo.isNull("brandName")) {
                jobEntity.setBossBrandName(bossInfo.getString("brandName"));
            }
            if (bossInfo.has("bossSource")) {
                jobEntity.setBossSource(bossInfo.getInt("bossSource"));
            }
            if (bossInfo.has("certificated")) {
                jobEntity.setBossCertificated(bossInfo.getBoolean("certificated"));
            }
            if (bossInfo.has("tagIconUrl") && !bossInfo.isNull("tagIconUrl")) {
                jobEntity.setBossTagIconUrl(bossInfo.getString("tagIconUrl"));
            }
            if (bossInfo.has("avatarStickerUrl") && !bossInfo.isNull("avatarStickerUrl")) {
                jobEntity.setBossAvatarStickerUrl(bossInfo.getString("avatarStickerUrl"));
            }

            log.debug("成功更新bossInfo信息到JobEntity");
        } catch (Exception e) {
            log.error("更新bossInfo信息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从brandComInfo节点更新JobEntity
     * 
     * @param jobEntity    JobEntity实例
     * @param brandComInfo brandComInfo JSON对象
     */
    private void updateJobEntityFromBrandComInfo(JobEntity jobEntity, JSONObject brandComInfo) {
        try {
            if (brandComInfo.has("encryptBrandId") && !brandComInfo.isNull("encryptBrandId")) {
                jobEntity.setEncryptBrandId(brandComInfo.getString("encryptBrandId"));
            }
            if (brandComInfo.has("brandName") && !brandComInfo.isNull("brandName")) {
                jobEntity.setBrandName(brandComInfo.getString("brandName"));
            }
            if (brandComInfo.has("logo") && !brandComInfo.isNull("logo")) {
                jobEntity.setBrandLogo(brandComInfo.getString("logo"));
            }
            if (brandComInfo.has("stage")) {
                jobEntity.setBrandStage(brandComInfo.getLong("stage"));
            }
            if (brandComInfo.has("stageName") && !brandComInfo.isNull("stageName")) {
                jobEntity.setBrandStageName(brandComInfo.getString("stageName"));
            }
            if (brandComInfo.has("scale")) {
                jobEntity.setBrandScale(brandComInfo.getLong("scale"));
            }
            if (brandComInfo.has("scaleName") && !brandComInfo.isNull("scaleName")) {
                jobEntity.setBrandScaleName(brandComInfo.getString("scaleName"));
            }
            if (brandComInfo.has("industry")) {
                jobEntity.setBrandIndustry(brandComInfo.getLong("industry"));
            }
            if (brandComInfo.has("industryName") && !brandComInfo.isNull("industryName")) {
                jobEntity.setBrandIndustryName(brandComInfo.getString("industryName"));
            }
            if (brandComInfo.has("introduce") && !brandComInfo.isNull("introduce")) {
                jobEntity.setBrandIntroduce(brandComInfo.getString("introduce"));
            }
            if (brandComInfo.has("labels")) {
                jobEntity.setBrandLabels(brandComInfo.getJSONArray("labels").toString());
            }
            if (brandComInfo.has("activeTime")) {
                jobEntity.setBrandActiveTime(brandComInfo.getLong("activeTime"));
            }
            if (brandComInfo.has("visibleBrandInfo")) {
                jobEntity.setVisibleBrandInfo(brandComInfo.getBoolean("visibleBrandInfo"));
            }
            if (brandComInfo.has("focusBrand")) {
                jobEntity.setFocusBrand(brandComInfo.getBoolean("focusBrand"));
            }
            if (brandComInfo.has("customerBrandName") && !brandComInfo.isNull("customerBrandName")) {
                jobEntity.setCustomerBrandName(brandComInfo.getString("customerBrandName"));
            }
            if (brandComInfo.has("customerBrandStageName") && !brandComInfo.isNull("customerBrandStageName")) {
                jobEntity.setCustomerBrandStageName(brandComInfo.getString("customerBrandStageName"));
            }

            log.debug("成功更新brandComInfo信息到JobEntity");
        } catch (Exception e) {
            log.error("更新brandComInfo信息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 通过访问zhipin.com主页来刷新token
     * 模拟正常用户访问行为，获取新的临时token
     * 
     * @return 是否成功刷新token
     */
    private boolean refreshTokenByVisitingZhipin() {
        try {
            log.info("开始访问zhipin.com刷新token");

            // 获取Playwright上下文
            BrowserContext context = PlaywrightUtil.getContext();
            if (context == null) {
                log.error("无法获取Playwright上下文，token刷新失败");
                return false;
            }

            // 创建新页面访问zhipin.com
            Page refreshPage = context.newPage();

            try {
                // 设置用户代理，模拟真实浏览器访问
                refreshPage.setExtraHTTPHeaders(Map.of(
                        "User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8",
                        "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                        "Accept-Encoding", "gzip, deflate, br",
                        "Cache-Control", "no-cache",
                        "Pragma", "no-cache"));

                // 访问zhipin.com主页
                log.info("正在访问 https://www.zhipin.com/");
                refreshPage.navigate("https://www.zhipin.com/");

                // 等待页面加载完成
                refreshPage.waitForLoadState();
                refreshPage.waitForTimeout(3000); // 等待3秒确保页面完全加载

                // 模拟用户行为：滚动页面
                refreshPage.evaluate("window.scrollTo(0, document.body.scrollHeight / 2)");
                refreshPage.waitForTimeout(1000);

                // 模拟点击页面（不实际点击任何元素，只是模拟用户活动）
                refreshPage.hover("body");
                refreshPage.waitForTimeout(1000);

                log.info("成功访问zhipin.com，token刷新完成");
                return true;

            } finally {
                // 关闭页面
                if (refreshPage != null) {
                    refreshPage.close();
                }
            }

        } catch (Exception e) {
            log.error("访问zhipin.com刷新token时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

}
