#!/bin/bash
# 一次性在 VPS 上运行，设置自动部署轮询
# 使用方式：在 Vultr Console 粘贴执行

set -e

# 写入自动部署脚本
cat > /opt/auto-deploy.sh << 'DEPLOY_SCRIPT'
#!/bin/bash
REPO="mbr69359-stack/warehouse-backend"
JAR_PATH="/opt/warehouse.jar"
RELEASE_ID_FILE="/opt/.last-release-id"
LOG="/var/log/warehouse-deploy.log"

# 拉取最新 release 信息
RELEASE_JSON=$(curl -sf "https://api.github.com/repos/${REPO}/releases/latest" 2>/dev/null)
[ $? -ne 0 ] && exit 0

RELEASE_ID=$(echo "$RELEASE_JSON" | grep -m1 '"id":' | grep -o '[0-9]*')
ASSET_URL=$(echo "$RELEASE_JSON" | grep '"browser_download_url"' | grep '\.jar' | grep -o 'https://[^"]*' | head -1)

[ -z "$RELEASE_ID" ] && exit 0
[ -z "$ASSET_URL" ] && exit 0

# 已是最新版本，跳过
if [ -f "$RELEASE_ID_FILE" ] && [ "$(cat $RELEASE_ID_FILE)" = "$RELEASE_ID" ]; then
    exit 0
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 发现新版本 release-id=$RELEASE_ID，开始部署..." >> $LOG

# 下载
wget -q -O /opt/warehouse-new.jar "$ASSET_URL" 2>>$LOG
if [ $? -ne 0 ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 下载失败!" >> $LOG
    rm -f /opt/warehouse-new.jar
    exit 1
fi

# 停服、换包、启服
systemctl stop warehouse >> $LOG 2>&1
mv /opt/warehouse-new.jar /opt/warehouse.jar
systemctl start warehouse >> $LOG 2>&1
sleep 3

# 验证启动
if systemctl is-active --quiet warehouse; then
    echo "$RELEASE_ID" > "$RELEASE_ID_FILE"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 部署成功 ✓" >> $LOG
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 启动失败！查看 journalctl -u warehouse" >> $LOG
fi
DEPLOY_SCRIPT

chmod +x /opt/auto-deploy.sh

# 添加 cron：每分钟检查一次
(crontab -l 2>/dev/null | grep -v auto-deploy.sh; echo "* * * * * root /opt/auto-deploy.sh") | crontab -

# 也同步写入 /etc/cron.d 防止 root crontab 没生效
cat > /etc/cron.d/warehouse-deploy << 'EOF'
* * * * * root /opt/auto-deploy.sh
EOF

echo "✓ 安装完成！"
echo "  脚本：/opt/auto-deploy.sh"
echo "  日志：/var/log/warehouse-deploy.log"
echo "  每分钟自动检查 GitHub Release，有新版本自动更新"
echo ""
echo "手动测试："
echo "  /opt/auto-deploy.sh && tail -f /var/log/warehouse-deploy.log"