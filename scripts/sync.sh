#!/usr/bin/env bash
set -e

JSON_PATH="/opt/baddrop/src/main/resources/initial-cases.json"
ENV_PATH="/opt/baddrop/.env"

if [ ! -f "$JSON_PATH" ]; then
  echo "Ошибка: $JSON_PATH не найден!"
  exit 1
fi

echo "=== Синхронизация кейсов из $JSON_PATH в базу MySQL ==="

python3 - << 'PYEOF'
import json
import subprocess
import os

env_vars = {}
env_path = "/opt/baddrop/.env"
if os.path.exists(env_path):
    with open(env_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env_vars[k.strip()] = v.strip().strip("'").strip('"')

db_password = env_vars.get("MYSQL_ROOT_PASSWORD") or env_vars.get("DB_PASSWORD") or os.getenv("DB_PASSWORD", "")
db_name = env_vars.get("MYSQL_DATABASE") or env_vars.get("DB_NAME", "baddrop_db")

with open("/opt/baddrop/src/main/resources/initial-cases.json", "r", encoding="utf-8") as f:
    cases = json.load(f)

sql_statements = []
for c in cases:
    slug = c["slug"]
    name = c.get("name", slug)
    price = c["price"]
    img = c.get("imageUrl", "")
    active = 1 if c.get("active", True) else 0
    paytable = json.dumps(c.get("paytable", []))
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

full_sql = "\n".join(sql_statements)

pass_arg = f"-p{db_password}" if db_password else ""
cmd = [
    "docker", "compose", "exec", "-T", "mysql",
    "mysql", "-u", "root"
]
if pass_arg:
    cmd.append(pass_arg)
cmd.append(db_name)

proc = subprocess.run(cmd, input=full_sql, text=True, capture_output=True)
if proc.returncode != 0:
    print("Ошибка SQL:", proc.stderr)
    exit(1)

print(f"Успешно синхронизировано {len(cases)} кейсов!")
PYEOF

echo "=== Синхронизация завершена ==="