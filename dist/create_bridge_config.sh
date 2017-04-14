#!/bin/env bash -x
# Script to dynamically build bridge server config from env variables
# Brian Holt <beholt@gmail.com> 02.09.2017
mkdir -p ${HOME}/.bridge
cat <<-EOF > ${HOME}/.bridge/bridge-server.conf
bridge.env=${BRIDGE_ENV:-dummy-value}
bridge.user=${BRIDGE_USER:-dummy-value}

admin.email = ${ADMIN_EMAIL:-dummy-value}
admin.password = ${ADMIN_PASSWORD:-dummy-value}

async.worker.thread.count = ${ASYNC_WORKER_THREAD_COUNT:-20}

aws.key = ${AWS_KEY:-AWS}
aws.secret.key = ${AWS_SECRET_KEY:-AWS}

aws.key.upload = ${AWS_KEY_UPLOAD:-AWS}
aws.secret.key.upload = ${AWS_SECRET_KEY_UPLOAD:-AWS}

aws.key.upload.cms = ${AWS_KEY_UPLOAD_CMS:-AWS}
aws.secret.key.upload.cms = ${AWS_SECRET_KEY_UPLOAD_CMS:-AWS}

bridge.healthcode.key = ${BRIDGE_HEALTHCODE_KEY:-KST6Md7/phHLZg+1FBgbmngKi53K/e7gLptQOEDii0M=}
bridge.healthcode.redis.key = ${BRIDGE_HEALTHCODE_REDIS_KEY:-zEjhUL/FVsN8vti6HO27XgrM32i1a3huEuXWD4Hq06I=}

ddb.max.retries = ${DDB_MAX_RETRIES:-1}

${BRIDGE_ENV}.fphs.id.add.limit = ${FPHS_ID_ADD_LIMIT:-100}
${BRIDGE_ENV}.external.id.add.limit = ${EXTERNAL_ID_ADD_LIMIT:-100}
${BRIDGE_ENV}.host.postfix = ${HOST_POSTFIX:--develop.sagebridge.org}
${BRIDGE_ENV}.webservices.url = ${WEBSERVICES_URL:-https://webservices-develop.sagebridge.org}
${BRIDGE_ENV}.upload.bucket = ${DUPLOAD_BUCKET:-org-sagebridge-upload-develop}
${BRIDGE_ENV}.attachment.bucket = ${ATTACHMENT_BUCKET:-org-sagebridge-attachment-develop}
${BRIDGE_ENV}.upload.cms.cert.bucket = ${UPLOAD_CMS_CERT_BUCKET:-org-sagebridge-upload-cms-cert-develop}
${BRIDGE_ENV}.upload.cms.priv.bucket = ${UPLOAD_CMS_PRIV_BUCKET:-org-sagebridge-upload-cms-priv-develop}
${BRIDGE_ENV}.consents.bucket = ${CONSENTS_BUCKET:-org-sagebridge-consents-dev}
${BRIDGE_ENV}.udd.sqs.queue.url = ${UDD_SQS_QUEUE_URL:-https://sqs.us-east-1.amazonaws.com/649232250620/Bridge-WorkerPlatform-Request-dev}
${BRIDGE_ENV}.study.whitelist = ${STUDY_WHITELIST:-api}

email.unsubscribe.token = ${EMAIL_UNSUBSCRIBE_TOKEN:-dummy-value}

enterprise.stormpath.application.href = ${ENTERPRISE_STORMPATH_APPLICATION_HREF:-dummy-value}
enterprise.stormpath.id = ${ENTERPRISE_STORMPATH_ID:-dummy-value}
enterprise.stormpath.secret = ${ENTERPRISE_STORMPATH_SECRET:-dummy-value}

exporter.synapse.id = ${EXPORTER_SYNAPSE_ID:-3325672}

external.id.lock.duration = ${EXTERNAL_ID_LOCK_DURATION:-30000}

max.num.zip.entries = ${MAX_NUM_ZIP_ENTRIES:-100}
max.zip.entry.size = ${MAX_ZIP_ENTRY_SIZE:-25000000}

redis.max.total = ${REDIS_MAX_TOTAL:-50}
redis.min.idle = ${REDIS_MIN_IDLE:-3}
redis.max.idle = ${REDIS_MAX_IDLE:-50}
redis.timeout = ${REDIS_TIMEOUT:-2000}
redis.url = ${REDIS_URL:-redis://provider:password@localhost:6379}

route53.zone = ${ROUTE53_ZONE:-ZP0HNVK1V670D}
sns.key = ${SNS_KEY:-dummy-value}
sns.secret.key = ${SNS_SECRET_KEY:-dummy-value}

support.email = ${SUPPORT_EMAIL:-Bridge (Sage Bionetworks) <support@sagebridge.org>}

synapse.api.key = ${NAPSE_API_KEY:-your-api-key-here}
synapse.user = ${SYNAPSE_USR:-your-username-here}
sysops.email = ${SYSOP_EMAIL:-Bridge IT <bridge-testing+sysops@sagebase.org>}

test.synapse.user.id = ${TEST_SYNAPSE_USER_ID:-3348228}

upload.cms.certificate.city = ${UPLOAD_CMS_CERTIFICATE_CITY:-Seattle}
upload.cms.certificate.country = ${UPLOAD_CMS_CERTIFICATE_COUNTRY:-US}
upload.cms.certificate.email = ${UPLOAD_CMS_CERTIFICATE_EMAIL:-bridgeIT@sagebase.org}
upload.cms.certificate.organization = ${UPLOAD_CMS_CERTIFICATE_ORGANIZATION:-Sage Bionetworks}
upload.cms.certificate.state = ${UPLOAD_CMS_CERTIFICATE_STATE:-WA}
upload.cms.certificate.team = ${UPLOAD_CMS_CERTIFICATE_TEAM:-Bridge}
upload.dupe.study.whitelist = ${UPLOAD_DUPE_STUDY_WHITELIST:-api}
EOF