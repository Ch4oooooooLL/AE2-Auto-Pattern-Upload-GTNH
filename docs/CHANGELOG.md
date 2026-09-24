# 更新日志

发版时，`.github/workflows/release-tags.yml` 会按照 tag 名（如 `v1.2`）在本文件里找到对应的小节，
把它作为 GitHub Release 的更新日志；找不到匹配小节时回退到 GitHub 自动生成的 release notes。

标题请保持 `## <版本号>` 的写法（版本号与 tag 同名，可带日期后缀），版本之间的改动写在其小节内。

## v1.2 (2026-09-24)

适配 AE2 290beta3 与新版 GTNH 依赖，并增强配方搜索关键词：

- 适配 AE2 290beta3：依赖升级到 Applied-Energistics-2-Unofficial `rv3-beta-1050-GTNH`、
  NotEnoughItems `2.8.130-GTNH`、AE2FluidCraft-Rework `1.5.106-gtnh`；
  NotEnoughEnergistics 改用 Maven 依赖 `1.7.41`，不再依赖 `libs/` 下的本地 jar
- 搜索关键词支持编程电路号与 NC（不消耗）物品：按配方输入槽位顺序拼在配方基名之后，
  名称优先取游戏内本地化结果（装汉化时为中文）
- 目标接口样板槽已满时禁用上传按钮，并显示剩余可用槽位数
- 选择 ME 接口后自动返回上一界面，不再停留在选择列表

## v1.1.1

- 新增 F 键搜索
- 修正样板槽位已满时仍然会上传的问题

## v1.1.0

- 首个发布版本：样板终端上传按钮、NEI 配方的自定义网络同步
