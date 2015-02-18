package com.example

import org.hidetake.groovy.ssh.Ssh

/**
 * Created by naga on 2015/01/18.
 */
class CIServerProvisioning {

    def setup() {

        def ssh = Ssh.newService()
        ssh.remotes {
            webServer {
                host = '192.168.54.32'
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

                // Oracle JDK8
                // 下記URLを確認の上、同意する場合のみ実行してください。
                // http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
                def existsJava8 = execute('echo `java -version 2>&1 | grep version`')
                if (!existsJava8) {
                    println("installing Java8...")
                    executeSudo 'wget --no-verbose --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u25-b17/jdk-8u25-linux-x64.rpm'
                    executeSudo "rpm -ivh jdk-8u25-linux-x64.rpm"
                    execute "rm -f jdk-8u25-linux-x64.rpm"
                }

                // Tomcat8
                def existsTomcat8 = execute('echo `/sbin/chkconfig --list tomcat8`')
                if (!existsTomcat8) {
                    println("installing Tomcat8...")

                    executeSudo 'wget --no-verbose http://archive.apache.org/dist/tomcat/tomcat-8/v8.0.18/bin/apache-tomcat-8.0.18.tar.gz'

                    executeSudo "useradd -s /sbin/nologin tomcat8"
                    execute "tar -xzf ~/apache-tomcat-8.0.18.tar.gz"
                    execute "rm -f ~/apache-tomcat-8.0.18.tar.gz"
                    executeSudo "mkdir /opt/tomcat8"
                    executeSudo "mv ~/apache-tomcat-8.0.18 /opt/tomcat8"
                    executeSudo "chown -R tomcat8:tomcat8 /opt/tomcat8"
                    executeSudo "chmod -R 770 /opt/tomcat8/apache-tomcat-8.0.18/webapps /opt/tomcat8/apache-tomcat-8.0.18/temp /opt/tomcat8/apache-tomcat-8.0.18/logs /opt/tomcat8/apache-tomcat-8.0.18/work /opt/tomcat8/apache-tomcat-8.0.18/conf"
                    executeSudo "chown -R tomcat8:tomcat8 /opt/tomcat8/apache-tomcat-8.0.18/logs"

                    // 自動起動設定
                    put 'src/main/resources/tomcat8/init.d', '/home/vagrant/tomcat8'
                    executeSudo 'chmod +x /home/vagrant/tomcat8'
                    executeSudo 'mv /home/vagrant/tomcat8 /etc/rc.d/init.d/tomcat8'
                    executeSudo 'service tomcat8 start'
                    executeSudo 'chkconfig tomcat8 on'
                }

                // Jenkins
                def existsJenkins = execute('echo `sudo find /opt/tomcat8/apache-tomcat-8.0.18/webapps -name "jenkins"`')
                if (!existsJenkins) {
                    println("installing Jenkins...")

                    executeSudo 'wget --no-verbose http://mirrors.jenkins-ci.org/war/latest/jenkins.war'
                    executeSudo 'mv jenkins.war /opt/tomcat8/apache-tomcat-8.0.18/webapps'
                }

                // Gitbucket
                def existsGitbucket = execute('echo `sudo find /opt/tomcat8/apache-tomcat-8.0.18/webapps -name "gitbucket"`')
                if (!existsGitbucket) {
                    println("installing Gitbucket...")

                    executeSudo 'wget --no-verbose https://github.com/takezoe/gitbucket/releases/download/2.8/gitbucket.war'
                    executeSudo 'mv gitbucket.war /opt/tomcat8/apache-tomcat-8.0.18/webapps'
                }

                // Sshpass
                def existsSshpass = execute 'echo `yum list installed | grep sshpass`'
                if (!existsSshpass) {
                    executeSudo 'yum install sshpass -y'
                }

                // Docker
                def existsDocker = execute 'echo `yum list installed | grep docker`'
                if (!existsDocker) {
                    executeSudo 'wget -P /etc/yum.repos.d http://www.hop5.in/yum/el6/hop5.repo'
                    executeSudo 'yum install xz docker-io -y'
                    executeSudo 'service docker start'
                }

                // tomcat8にdockerをsudoなしで使用出来るよう設定
                executeSudo 'gpasswd -a tomcat8 docker'

                try {
                    executeSudo 'reboot'
                } catch(Exception e) {
                    // リブートでExceptionが返却される
                    println(e)
                }
            }
        }
    }

    static void main(String[] args) {
        new CIServerProvisioning().setup()
    }

}