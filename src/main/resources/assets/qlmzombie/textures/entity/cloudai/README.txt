# CloudAI 皮肤贴图存放目录
#
# 默认皮肤: default_skin.png (64x64 PNG, SLIM 细臂格式)
#   对应 CloudAiConstants.DEFAULT_SKIN_PATH (L43)
#
# 也可使用外部 URL 皮肤（CloudAiConstants.FALLBACK_SKINS），
# 此时无需本地 PNG，由 FakePlayerEntityRenderer 在线拉取。
#
# 推荐 64x64 / 64x32 皮肤 PNG：
#   - 若 DEFAULT_SKIN_MODEL = slim (L46): 使用细臂皮肤
#   - 若改为 default: 则使用粗臂皮肤
