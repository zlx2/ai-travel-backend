package com.sora.aitravel.common.result;

/**
 * 骨架阶段统一占位响应工具类。
 *
 * <p>在端点尚未接入真实业务实现时，使用 notImplemented() 返回 501 状态码， 避免用伪造数据制造"调用成功"的假象。 实现对应 Service 后应逐步删除对
 * notImplemented() 的调用。
 */
public final class ScaffoldResponses {
    private ScaffoldResponses() {}

    /**
     * 返回"接口已生成但业务逻辑未实现"的占位响应。
     *
     * @param <T> 数据类型
     * @return 501 状态码的失败响应
     */
    public static <T> R<T> notImplemented() {
        return R.fail(501, "接口骨架已生成，业务逻辑待实现");
    }
}
