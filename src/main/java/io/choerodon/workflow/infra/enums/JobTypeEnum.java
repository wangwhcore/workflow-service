package io.choerodon.workflow.infra.enums;

/**
 * 〈功能简述〉
 * 〈〉
 *
 * @author wanghao
 * @since 2020/4/3 16:57
 */
public enum JobTypeEnum {
    /**
     * cd 部署任务
     */
    CD_DEPLOY("cdDeploy"),

    /**
     * cd 审核任务
     */
    CD_AUDIT("cdAudit"),

    /**
     * cd 主机部署任务
     */
    CD_HOST("cdHost"),

    /**
     * cd API测试任务
     */
    CD_API_TEST("cdApiTest");


    private final String value;

    JobTypeEnum(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
