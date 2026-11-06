#!/bin/bash
set -e

echo "===> Пересборка контейнера app..."
docker compose build --no-cache app

echo "===> Запуск обновленного контейнера..."
docker compose up -d app

echo "===> Логи приложения (выход: Ctrl + C)..."
docker compose logs -f --tail=40 app