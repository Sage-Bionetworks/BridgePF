Bridge (Sage Bionetworks)
=========================================

[![Build Status](https://travis-ci.org/Sage-Bionetworks/BridgePF.svg?branch=develop)](https://travis-ci.org/Sage-Bionetworks/BridgePF)

Development
------------------
##### Prerequisites

1. Install [VirtualBox](https://www.virtualbox.org/) and [Vagrant](https://www.vagrantup.com/).
2. Create the local configuration file `~/.sbt/bridge.conf`. Make sure the file is accessible only by you. In this file, you will need to specify the resource locations and their corresponding credentials. See the template, `conf/bridge.conf`, in source code.

##### How to test locally

1. Initialize Vagrant `vagrant up`.
2. Connect to the Vagrant box `vagrant ssh`.
3. In the Vagrant box, go to the shared project folder `cd /vagrant`.
4. Launch the Play console `activator`.
5. Within the Play console, run `test`.

Deployment
------------------
[How to deploy, rollback, and patch](https://github.com/Sage-Bionetworks/BridgePF/wiki/Production%20Deployment)

Documentation
------------------
* [REST API](https://sagebionetworks.jira.com/wiki/display/BRIDGE/Bridge+REST+API)
* [Security Overview](https://sagebionetworks.jira.com/wiki/display/BRIDGE/Security+Overview)
* [Java SDK](https://github.com/Sage-Bionetworks/BridgeJavaSDK)
* [iOS SDK](https://github.com/Sage-Bionetworks/Bridge-iOS-SDK)

2