# Tiny sidecar that dumps the MySQL database once a day. Uses the same image family as the database,
# so mysqldump always matches the server version (MySQL 8 auth works out of the box).
FROM mysql:8.4
RUN command -v curl >/dev/null 2>&1 || (microdnf install -y curl && microdnf clean all)
COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh
ENTRYPOINT ["/usr/local/bin/backup.sh"]
