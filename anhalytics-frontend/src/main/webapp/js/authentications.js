var authenticateIdilia = function (requestText) {
    var today = new Date();
    var todayString = today.toUTCString();

    var hash = CryptoJS.MD5(requestText);
    var encodedData = hash.toString(CryptoJS.enc.Base64);

    // Create a string with the concatenation of the date, host, request URI, and content MD5. 
    // Adding a hyphen between each.
    var idString = todayString + "-" + "api.idilia.com" + "-" + "/1/text/paraphrase.mpjson" + "-" + encodedData;
    var accessKey = "IdiXy2ySped9t";
    var key = "sYiJokXYwkQJMgEEpPn6LEwUbo7Hzr";

    var signature = CryptoJS.HmacSHA256(idString, key);
    var encodedSignature = signature.toString(CryptoJS.enc.Base64);

    var header = [];
    header.push({'Host': 'api.idilia.com'});
//			header.push( {'Accept' : 'application/json'} );
    header.push({'Date': todayString});
    header.push({'Authorization': "IDILIA " + accessKey + ":" + encodedSignature});
    return header;
}

var authenticateSummons = function (queryParameters) {
    var today = new Date();
    var todayString = today.toUTCString();
    var idString = computeIdString("application/json", todayString, "api.summon.serialssolutions.com",
            "/2.0.0/search", queryParameters);
    var digest = buildDigest("6T1U6Vjs0u0D0QN1m0M3edK5DgpAAXPp", idString);

    var header = [];
    header.push({'Host': 'api.summon.serialssolutions.com'});
    header.push({'Accept': 'application/json'});
    header.push({'x-summon-date': todayString});
    header.push({'Authorization': 'Summon epo;' + digest});
    return header;
}