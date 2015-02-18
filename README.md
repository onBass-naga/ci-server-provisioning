ci-server-provisioning
------------------------------------

groovy-sshを用いて、VirtualBoxの仮想マシンをCIサーバ化するサンプルです。
CentOS6.5へ下記をインストールします。
* Java8 (Oracle JDK8)
* Tomcat8
* Gitbucket2.7
* Jenkins (Latest)

動作検証はVagrant 1.7.2を用いて行いました。
```
config.vm.box_url = "https://github.com/2creatives/vagrant-centos/releases/download/v6.5.3/centos65-x86_64-20140116.box"
config.vm.network "private_network", ip: "192.168.33.11"
```
