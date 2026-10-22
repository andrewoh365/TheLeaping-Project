FROM postgres:16

ENV PGDATA=/var/lib/postgresql/data/pgdata

COPY entrypoint.sh /usr/local/bin/reporting-replica-entrypoint.sh
RUN chmod 0755 /usr/local/bin/reporting-replica-entrypoint.sh

USER root
ENTRYPOINT ["/usr/local/bin/reporting-replica-entrypoint.sh"]
CMD ["postgres"]
