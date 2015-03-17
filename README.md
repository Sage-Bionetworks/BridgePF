Bridge (Sage Bionetworks)
=========================================

[![Build Status](https://travis-ci.org/Sage-Bionetworks/BridgePF.svg?branch=develop)](https://travis-ci.org/Sage-Bionetworks/BridgePF)

Development
------------------
##### Prerequisites

1. Install VirtualBox and Vagrant.
2. Create the configuration file `~/.sbt/bridge.conf`.

##### How to test locally

1. Initialize Vagrant `vagrant up`.
2. Connect to the Vagrant box `vagrant ssh`.
3. In the Vagrant box, go to the shared project folder `cd /vagrant`.
4. Launch the Play console `activator`.
5. Within Play console, run `test`.

Deployment
------------------
[How to deploy, rollback, and patch](https://github.com/Sage-Bionetworks/BridgePF/wiki/Production%20Deployment)

Documentation
------------------
* [REST API](https://sagebionetworks.jira.com/wiki/display/BRIDGE/Bridge+REST+API)
* [Java SDK](https://github.com/Sage-Bionetworks/BridgeJavaSDK)
* [iOS SDK](https://github.com/Sage-Bionetworks/Bridge-iOS-SDK)
