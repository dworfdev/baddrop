cat << 'EOF' > /opt/baddrop/sync-cases.sh
#!/usr/bin/env bash
set -e

JSON_PATH="/opt/baddrop/src/main/resources/initial-cases.json"

if [ ! -f "$JSON_PATH" ]; then
  echo "Ошибка: $JSON_PATH не найден!"
  exit 1
fi

echo "=== Синхронизация кейсов из $JSON_PATH в базу MySQL ==="

# Читаем JSON через python3 (он всегда есть на Ubuntu) и генерируем SQL-запросы
python3 - <<PYEOF
import json
import subprocess
import os

with open("$JSON_PATH", "r", encoding="utf-8") as f:
    cases = json.load(f)

sql_statements = []
for c in cases:
    slug = c["slug"]
    name = c.get("name", slug)
    price = c["price"]
    img = c.get("imageUrl", "")
    active = 1 if c.get("active", True) else 0
    paytable = json.dumps(c.get("paytable", []))
    # Экранируем одинарные кавычки для SQL
    paytable_sql = paytable.replace("'", "''")

    stmt = f"""
    INSERT INTO cases (slug, name, price, image_url, is_active, paytable_json, created_at)
    VALUES ('{slug}', '{name}', {price}, '{img}', {active}, '{paytable_sql}', NOW())
    ON DUPLICATE KEY UPDATE
      name = VALUES(name),
      price = VALUES(price),
      image_url = VALUES(image_url),
      is_active = VALUES(is_active),
      paytable_json = VALUES(paytable_json);
    """
    sql_statements.append(stmt)

full_sql = "\\n".join(sql_statements)

# Выполняем в контейнере baddrop_mysql
db_password = os.getenv("DB_PASSWORD", "")
cmd = [
    "docker", "compose", "exec", "-T", "mysql",
    "mysql", "-u", "root", f"-p{db_password}", "baddrop_db"
]

proc = subprocess.run(cmd, input=full_sql, text=True, capture_output=True)
if proc.returncode != 0:
    print("Ошибка SQL:", proc.stderr)
    exit(1)

print(f"Успешно обработано {len(cases)} кейсов!")
PYEOF

echo "=== Синхронизация завершена ==="
EOF

chmod +x /opt/baddrop/sync-cases.sh