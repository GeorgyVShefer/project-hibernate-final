docker-compose exec postgres pgloader \
mysql://root:root@mysql:3306/world \
postgresql://postgres:root@postgres:5432/postgres
sleep 100