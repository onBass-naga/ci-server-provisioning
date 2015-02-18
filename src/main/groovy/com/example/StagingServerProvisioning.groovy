package com.example

import groovy.sql.Sql
import org.hidetake.groovy.ssh.Ssh

/**
 * Created by naga on 2015/01/18.
 */
class StagingServerProvisioning {

    def setup() {

        def ssh = Ssh.newService()
        ssh.remotes {
            webServer {
                host = '192.168.54.33'
                port = 22
                user = 'vagrant'
                password = 'vagrant'
                knownHosts = allowAnyHosts
            }
        }

        ssh.run {
            session(ssh.remotes.webServer) {

                // 日本語設定
                def isJST = execute('echo `date | grep JST`')
                if (!isJST) {
                    executeSudo 'yum update -y'
                    executeSudo 'yum -y groupinstall "Japanese Support"'
                    executeSudo 'localedef -f UTF-8 -i ja_JP ja_JP.utf8'

                    executeSudo "sed -e 's/en_US/ja_JP/g' /etc/sysconfig/i18n > ~/i18n"
                    execute 'chmod 644 i18n'
                    executeSudo 'mv i18n /etc/sysconfig/'

                    executeSudo "sed -e 's/Etc/Asia/g' -e 's/UTC/Tokyo/g' /etc/sysconfig/clock > ~/clock"
                    execute 'chmod 644 clock'
                    executeSudo 'mv clock /etc/sysconfig/'

                    executeSudo 'sudo cp /usr/share/zoneinfo/Japan /etc/localtime'
                }

                // Wget
                def existsWget = execute 'echo `yum list installed | grep wget`'
                if (!existsWget) {
                    executeSudo 'yum install wget -y'
                }

                // Docker
                def existsDocker = execute 'echo `yum list installed | grep docker`'
                if (!existsDocker) {
                    executeSudo 'wget -P /etc/yum.repos.d http://www.hop5.in/yum/el6/hop5.repo'
                    executeSudo 'yum install xz docker-io -y'
                    executeSudo 'service docker start'
                }

                // PostgreSQL 9.4
                def existsPostgreSQL94 = execute 'echo `yum list installed | grep postgresql94`'
                if (!existsPostgreSQL94) {
                    put 'src/main/resources/postgresql94/pg_hba.conf', '/vagrant'
                    put 'src/main/resources/postgresql94/postgresql.conf', '/vagrant'

                    execute 'wget --no-verbose http://yum.postgresql.org/9.4/redhat/rhel-6-x86_64/pgdg-centos94-9.4-1.noarch.rpm'
                    executeSudo 'rpm -ivh ~/pgdg-centos94-9.4-1.noarch.rpm'
                    executeSudo 'yum install postgresql94-server -y'
                    execute 'rm -f ~/pgdg-centos94-9.4-1.noarch.rpm'
                    executeSudo 'service postgresql-9.4 initdb'

                    executeSudo 'cp /var/lib/pgsql/9.4/data/postgresql.conf /var/lib/pgsql/9.4/data/postgresql.conf.bk'
                    executeSudo 'cp /var/lib/pgsql/9.4/data/pg_hba.conf /var/lib/pgsql/9.4/data/pg_hba.conf.bk'
                    executeSudo 'mv /vagrant/postgresql.conf /var/lib/pgsql/9.4/data'
                    executeSudo 'mv /vagrant/pg_hba.conf /var/lib/pgsql/9.4/data'
                    executeSudo 'chmod 600 /var/lib/pgsql/9.4/data/postgresql.conf /var/lib/pgsql/9.4/data/pg_hba.conf'
                    executeSudo 'chown postgres:postgres /var/lib/pgsql/9.4/data/postgresql.conf /var/lib/pgsql/9.4/data/pg_hba.conf'

                    executeSudo 'service postgresql-9.4 start'
                    executeSudo 'chkconfig postgresql-9.4 on'
                }

                // Deployユーザー作成
                def existsDeployer = execute 'echo `cut -d: -f1 /etc/passwd | grep deployer`'
                if (!existsDeployer) {
                    executeSudo 'useradd -g docker deployer'
                    execute('sudo passwd deployer', pty: true, interaction: {

                        when(line: _) {
                            when(partial: _) {
                                standardInput << 'hogehogehoge' << '\n'
                                when(partial: _) {
                                    standardInput << 'hogehogehoge' << '\n'
                                }
                            }
                        }
                    })
                }
            }
        }
    }

    def setup2() {

        def ssh = Ssh.newService()
        ssh.remotes {
            webServer {
                host = '192.168.54.33'
                port = 22
                user = 'deployer'
                password = 'hogehogehoge'
                knownHosts = allowAnyHosts
            }
        }

        ssh.run {
            session(ssh.remotes.webServer) {

                // Docker起動シェル作成
                def existsRunShell = execute 'echo `ls | grep run.sh`'
                if (!existsRunShell) {
                    execute 'echo \'#!/bin/sh\' >> run.sh'
                    execute 'echo \'docker pull naga/spboot-sample\' >> run.sh'
                    execute 'echo \'docker ps -q | xargs docker kill\' >> run.sh'
                    execute 'echo \'docker run -d -p 80:8080 -e DB_URL=$DB_URL -e DB_USERNAME=$DB_USERNAME -e DB_PASSWORD=$DB_PASSWORD naga/spboot-sample\' >> run.sh'
                }

                // 環境変数にDB値を設定
                def hasWrittenDB = execute 'echo $DB_URL'
                if (!hasWrittenDB) {
                    execute 'echo \'export DB_URL=jdbc:postgresql://192.168.54.33:5432/dev\' >> /home/deployer/.bashrc'
                    execute 'echo \'export DB_USERNAME=devuser\' >> /home/deployer/.bashrc'
                    execute 'echo \'export DB_PASSWORD=password\' >> /home/deployer/.bashrc'

                    execute 'source /home/deployer/.bashrc'
                }

                // アプリ用DB作成
                def sql = Sql.newInstance("jdbc:postgresql://192.168.54.33:5432/", "postgres",
                        "password", 'org.postgresql.Driver'
                )
                sql.execute("CREATE USER devuser WITH SUPERUSER PASSWORD 'password'")
                sql.execute("CREATE DATABASE dev")

            }
        }
    }

    static void main(String[] args) {
        new StagingServerProvisioning().setup()
        new StagingServerProvisioning().setup2()
    }
}