#!/usr/bin/env bash
# TimeBook 源码 + 安装包一键发布到 GitHub
# 用法：GITHUB_TOKEN=ghp_xxx bash 发布到GitHub.sh
# Token 只存在于本次进程的环境变量里，不写入任何文件

set -euo pipefail

OWNER="CodeNerverEnd"
REPO="TimeBook"
EXE="downloads/TimeBook-2.1.0-桌面版.exe"
TAG="v2.1.0"

if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "错误：请先设置 GITHUB_TOKEN 环境变量"
  echo "  GITHUB_TOKEN=ghp_xxx bash 发布到GitHub.sh"
  exit 1
fi

cd "$(dirname "$0")"

echo "== 1/5 确认仓库 =="
if curl -sS -f -H "Authorization: Bearer $GITHUB_TOKEN" \
     -H "Accept: application/vnd.github+json" \
     "https://api.github.com/repos/$OWNER/$REPO" > /dev/null 2>&1; then
  echo "仓库 $OWNER/$REPO 已存在，跳过创建"
else
  echo "创建仓库 $OWNER/$REPO ..."
  curl -sS -f -X POST \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/user/repos" \
    -d "{\"name\":\"$REPO\",\"description\":\"柳比歇夫时间统计法 · 本地优先的计时与计划工具（Windows + Android）\",\"homepage\":\"\",\"private\":false,\"has_issues\":true,\"has_wiki\":false}" > /dev/null
  echo "仓库已创建"
fi

echo "== 2/5 提交源码 =="
git add -A
if git diff --cached --quiet; then
  echo "没有待提交的改动"
else
  git commit -m "TimeBook v2.1.0：Windows 桌面版 + 安卓版 + 官网 + README"
fi

echo "== 3/5 推送 =="
git remote remove origin 2>/dev/null || true
git remote add origin "https://$GITHUB_TOKEN@github.com/$OWNER/$REPO.git"
git branch -M main
git push -u origin main
git remote set-url origin "https://github.com/$OWNER/$REPO.git"
echo "推送完成，本地 remote 已清除 Token"

echo "== 4/5 创建 Release =="
RELEASE_JSON=$(curl -sS -f -X POST \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/releases" \
  -d "{\"tag_name\":\"$TAG\",\"name\":\"TimeBook $TAG\",\"body\":\"Windows 便携版（110 MB），双击即用；安卓 APK 在仓库 downloads/。功能与下载说明见 README。\",\"draft\":false,\"prerelease\":false}")
UPLOAD_URL=$(printf '%s' "$RELEASE_JSON" | sed -n 's/.*"upload_url": "\([^"]*\)".*/\1/p')
UPLOAD_URL="${UPLOAD_URL%\{*}"
echo "Release 已创建：$TAG"

echo "== 5/5 上传 Windows exe =="
if [ -f "$EXE" ]; then
  # 用规范英文名上传：GitHub 对 URL 里的中文名会返回 400，且会丢字符
  ASSET_NAME="TimeBook-${TAG#v}-Windows.exe"
  curl -sS -f -X POST \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@$EXE" \
    "${UPLOAD_URL}?name=${ASSET_NAME}" > /dev/null
  echo "exe 已上传（$(du -h "$EXE" | cut -f1)），文件名 $ASSET_NAME"
else
  echo "警告：未找到 $EXE，跳过"
fi

echo ""
echo "全部完成："
echo "  仓库 https://github.com/$OWNER/$REPO"
echo "  下载 https://github.com/$OWNER/$REPO/releases/tag/$TAG"
echo "请到 GitHub 删除本次使用的 Token。"
