package com.example.pat.pet

/**
 * 虚拟宠物数据模型。
 *
 * 当前为占位结构，未来将扩展为包含以下属性：
 * - 角色外观/皮肤
 * - 属性值（健康、心情、亲密度、精力）
 * - 角色名
 *
 * 当前阶段仅定义基础骨架，不实现养宠逻辑。
 *
 * 参考文档：7.3 角色属性系统（未来扩展）
 */
data class Pet(
    val name: String = "Pat",
    val type: PetType = PetType.DEFAULT
)

/** 宠物类型枚举。预留多角色支持。 */
enum class PetType {
    DEFAULT,
    CAT,
    DOG
}
