var titles = {
    'api': 'API',
    'asthma': 'Asthma Study',
    'breastcancer': 'Share the Journey',
    'cardiovascular': 'MyHeartCounts',
    'diabetes': 'GlucoSuccess',
    'parkinson': 'mPower'
};

var qd = {};
location.search.substr(1).split("&").forEach(function(item) {var k = item.split("=")[0], v = decodeURIComponent(item.split("=")[1]); (k in qd) ? qd[k].push(v) : qd[k] = [v,]});

var host = document.location.host.match(/^(.*?)\W/)[1];
if (!titles[host]) {
    host = "api";
}

setTimeout(function() {
    document.title = titles[host] + " " + document.title;
    $("#logo").attr("src","/mobile/images/"+host+".svg");
}, 1);
