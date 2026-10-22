FROM postgres:16

COPY entrypoint.sh /usr/local/bin/leap-primary-entrypoint.sh
COPY replication-hba.sh /docker-entrypoint-initdb.d/01-leap-replication-hba.sh
COPY replication-hba.sh /usr/local/bin/leap-replication-hba.sh

RUN chmod 0755 \
    /usr/local/bin/leap-primary-entrypoint.sh \
    /docker-entrypoint-initdb.d/01-leap-replication-hba.sh \
    /usr/local/bin/leap-replication-hba.sh

ENTRYPOINT ["/usr/local/bin/leap-primary-entrypoint.sh"]
CMD ["postgres"]