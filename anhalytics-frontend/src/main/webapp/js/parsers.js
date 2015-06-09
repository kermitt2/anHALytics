// read the result object and return useful vals based on ES results
// returns an object that contains things like ["data"] and ["facets"]
var parseresultsElasticSearch = function (dataobj) {
    var resultobj = new Object();
    resultobj["records"] = new Array();
    resultobj["highlights"] = new Array();
    resultobj["scores"] = new Array();
    resultobj["ids"] = new Array();
    resultobj["start"] = "";
    resultobj["found"] = "";
    resultobj["took"] = "";
    resultobj["facets"] = new Object();
    resultobj["facets2"] = new Object();
    resultobj["facets3"] = new Object();
    resultobj["aggregations"] = new Object();
    for (var item in dataobj.hits.hits) {
        resultobj["records"].push(dataobj.hits.hits[item].fields);
        resultobj["highlights"].push(dataobj.hits.hits[item].highlight);
        resultobj["scores"].push(dataobj.hits.hits[item]._score);
        resultobj["ids"].push(dataobj.hits.hits[item]._id);
        resultobj["start"] = "";
        resultobj["found"] = dataobj.hits.total;
        resultobj["took"] = dataobj.took;
    }
    for (var item in dataobj.facets) {
        var facetsobj = new Object();
        for (var thing in dataobj.facets[item]["terms"]) {
            facetsobj[ dataobj.facets[item]["terms"][thing]["term"] ] =
                    dataobj.facets[item]["terms"][thing]["count"];
        }
        for (var thing in dataobj.facets[item]["entries"]) {
            facetsobj[ dataobj.facets[item]["entries"][thing]["time"] ] =
                    dataobj.facets[item]["entries"][thing]["count"];
        }
        resultobj["facets"][item] = facetsobj;
    }
    for (var item in dataobj.facets) {
        resultobj["facets2"][item] = dataobj.facets[item]["terms"];
    }
    for (var item in dataobj.facets) {
        resultobj["facets3"][item] = dataobj.facets[item]["entries"];
    }
    for (var item in dataobj.aggregations) {
        resultobj["aggregations"][item] = dataobj.aggregations[item]["buckets"];
    }
    return resultobj;
}

// read the result object and return useful vals based on Summons results
// returns an object that contains things like ["data"] and ["facets"]
var parseresultsSummons = function (dataobj) {
    var resultobj = new Object();
    resultobj["records"] = new Array();
    resultobj["highlights"] = new Array();
    resultobj["scores"] = new Array();
    resultobj["facets"] = new Object();
    resultobj["start"] = "";

    resultobj["took"] = dataobj['totalRequestTime'];
    resultobj["found"] = dataobj['recordCount'];
    options.sessionId = dataobj['sessionId'];
    resultobj["pageCount"] = dataobj['pageCount'];
    resultobj["pageSize"] = dataobj['query']['pageSize'];

    resultobj["suggest"] = dataobj['didYouMeanSuggestions'];

    for (var item in dataobj.documents) {
        resultobj["records"].push(dataobj.documents[item]);
    }

    for (var item in dataobj.facetFields) {
        //console.log(item);
        var facetsobj = new Object();
        var field = dataobj.facetFields[item].displayName;
        var facetDone = [];
        if (field != "PublicationDate") {
            var nbAdded = 0;
            for (var thing in dataobj.facetFields[item]["counts"]) {
                //console.log(field);
                if (field == 'Discipline') {
                    if (options.search_index == 'summon') {
                        if (options.scope == 'scientific') {
                            //console.log(dataobj.facetFields[item]["counts"][thing]["value"].toLowerCase());
                            if (scientificDisciplinesLow.indexOf(dataobj.facetFields[item]["counts"][thing]["value"].toLowerCase()) == -1) {
                                continue;
                            }
                        }
                    }
                }

                if ($.inArray(dataobj.facetFields[item]["counts"][thing]["value"].toLowerCase(),
                        facetDone) == -1) {
                    facetsobj[ dataobj.facetFields[item]["counts"][thing]["value"] ] =
                            dataobj.facetFields[item]["counts"][thing]["count"];
                    nbAdded++;
                    facetDone.push(dataobj.facetFields[item]["counts"][thing]["value"].toLowerCase());
                    var theSize = 0;
                    for (var truc in options.facets) {
                        if (options.facets[truc]['field'] == field) {
                            theSize = options.facets[truc]['size'];
                            break;
                        }
                    }
                    if (nbAdded == theSize) {
                        break;
                    }
                }
            }
        }

        resultobj["facets"][field] = facetsobj;
    }
    for (var item in dataobj.facets) {
        resultobj["facets2"][item] = resultobj["facets"][item];
    }
    for (var item in dataobj.facets) {
        resultobj["facets3"][item] = resultobj["facets"][item];
    }

    // now for the dates
    if (dataobj.rangeFacetFields.length > 0) {
        var facetsobj = new Object();
        resultobj["facets2"] = {};
        resultobj["facets3"] = {};
        for (var item in dataobj.rangeFacetFields[0]["counts"]) {
            var field = dataobj.rangeFacetFields[0].displayName;
            var rangeDate = "" + dataobj.rangeFacetFields[0]["counts"][item]["range"]["minValue"];
            var theDate = new Date(parseInt(rangeDate), 0, 1, 0, 0, 0, 0);
            var timing = theDate.getTime();
            facetsobj[ timing ] =
                    dataobj.rangeFacetFields[0]["counts"][item]["count"];

            resultobj["facets"][field] = facetsobj;
            resultobj["facets2"][field] = []
            if (!resultobj["facets3"][field])
                resultobj["facets3"][field] = []
            resultobj["facets3"][field].push(
                    {'count': dataobj.rangeFacetFields[0]["counts"][item]["count"], 'time': timing});
        }
    }

    return resultobj;
}