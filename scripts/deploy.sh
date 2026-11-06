cat << 'EOF' > /opt/baddrop/deploy.sh
#!/bin/bash
set -e

echo "===> Стягивание свежего кода из GitHub..."
git fetch origin main
git reset --hard origin/main
chmod +x /opt/baddrop/deploy.sh || true
chmod +x /opt/baddrop/scripts/*.sh || true

echo "===> Пересборка и запуск контейнера app..."
docker compose build --no-cache app
docker compose up -d app

echo "===> Вывод свежих логов (выход: Ctrl + C)..."
docker compose logs -f --tail=40 app
EOF
chmod +x /opt/baddrop/deploy.sh