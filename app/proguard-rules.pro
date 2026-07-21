# ---------------------------------------------------------
# 1. 保护魔方 3D 模型与状态定义类
# ---------------------------------------------------------
-keep class com.example.cubesolver.ui.Vector3 { *; }
-keep class com.example.cubesolver.ui.Quad { *; }
-keep class com.example.cubesolver.ui.ProjectedQuad { *; }

# ---------------------------------------------------------
# 2. 保护 JNI 交互的类与回调接口 (核心修复点)
# ---------------------------------------------------------

# 保持 CubeSolver 类及其所有 native 方法不被混淆或剪裁
-keep class com.example.cubesolver.utils.CubeSolver {
    native <methods>;
}

# 保持 SearchCallback 接口及其内部所有方法名不被重命名（防止 JNI GetMethodID 报错）
-keep class com.example.cubesolver.utils.SearchCallback { *; }

# 保持传递给 UI 的状态密封类不被混淆
-keep class com.example.cubesolver.utils.SearchStatus** { *; }

# ---------------------------------------------------------
# 3. 常见的通用保护
# ---------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod

# 保持项目中所有 native 方法的底层符号关联
-keepclasseswithmembernames class * {
    native <methods>;
}