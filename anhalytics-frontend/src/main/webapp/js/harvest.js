
var harvest = function (event) {
    event.preventDefault()

    $('#facetview').append(getHarvestModal(options.data.found));

    $('.facetview_removeharvest').bind('click', removeharvest);
    $('#facetview_doharvest').bind('click', doharvest);
    $('#facetview_harvestmodal').modal('show');
}

// run the harvesting
var doharvest = function (event) {
    event.preventDefault();
    var dbName = $('#input_dbname').val();

    console.log($('#input_couchdb').val());
    console.log(dbName);
    console.log($('#input_from').val());
    console.log($('#input_to').val());
    console.log($('#step_size').val());

    $('#harvest_progress').width('0%');

    couchUrlPrefix = "http://" + $('#input_couchdb').val();
    // default: "http://localhost:5984";

    // range of results
    var from = parseInt($('#input_from').val());
    var to = parseInt($('#input_to').val());
    $.ajax({
        url: couchUrlPrefix + "/" + dbName,
        type: 'put',
        dataType: 'json',
        success: function (data) {
            console.log(data);
        },
        error: function (status) {
            console.log(status);
        }
    });
    var step = parseInt($('#step_size').val());

    $('#info_progress').html('<span stype="color:red;">0 / ' + (to - from) + '</span>');

    // get results	
    var N = Math.floor((to - from) / step) + 1;
    console.log('N = ' + N);
    // via summon
    //for(var i=0; i<N; i++) {
    doSetTimeout(step, 0, N);
    //}
}

// remove the harvest modal from page
var removeharvest = function (event) {
    event.preventDefault()
    $('#facetview_harvestmodal').modal('hide')
    $('#facetview_harvestmodal').remove()
}


var doSetTimeout = function (step, i, N) {
    var j = i;
    var interval = parseInt($('#input_interval').val());
    if (j == 0) {
        callHarvest(step, j, N);
    }
    else {
        setTimeout(function () {
            callHarvest(step, j, N);
        }, 3000);
    }
}

var callHarvest = function (step, i, N) {
    var queryParameters = summonsquery(step, i + 1);
    var header0 = authenticateSummons(queryParameters);
    queryParameters = summonsquery(step, i + 1);
    var queryString = "";
    var first = true;
    for (var param in queryParameters) {
        var obj = queryParameters[param];
        for (var key in obj) {
            if (first) {
                queryString = key + '=' + queryParameters[param][key];
                first = false;
            }
            else {
                queryString += '&' + key + '=' + queryParameters[param][key];
            }
        }
    }

    if (options.service == 'proxy') {
        // ajax service access via a proxy
        for (var param in header0) {
            var obj = header0[param];
            for (var key in obj) {
                queryString += '&' + key + '=' + encodeURIComponent(header0[param][key]);
            }
        }

        var proxy = options.proxy_host + "/proxy-summon.jsp?";
        $.ajax({
            type: "get",
            url: proxy,
            contentType: 'application/json',
            dataType: 'jsonp',
            data: queryString,
            success: harvestresults
        });
    }
    else {
        // ajax service access is local
        $.ajax({
            type: "get",
            url: options.search_url,
            contentType: 'application/json',
            dataType: 'json',
            beforeSend: function (xhr) {
                for (var param in header0) {
                    var obj = header0[param];
                    for (var key in obj) {
                        xhr.setRequestHeader(key, header0[param][key]);
                    }
                }
            },
            data: queryString,
            success: harvestresults
        });
    }

    var progress = Math.floor(((i + 1) / N) * 100);
    $('#harvest_progress').width(progress + '%');
    //console.log(progress+'%');
    var from = parseInt($('#input_from').val());
    var to = parseInt($('#input_to').val());
    $('#info_progress').html('<span stype="color:red;">' + (i * N) + ' / ' + (to - from) + '</span>');

    if (i + 1 < N)
        doSetTimeout(step, i + 1, N);
    $('#info_progress').html('<span stype="color:red;">' + (to - from) + ' / ' + (to - from) + ' - Complete! </span>');
}


// put the results in couchdb
var harvestresults = function (sdata) {
    var data = parseresultsSummons(sdata);
    var couchUrlPrefix = "http://" + $('#input_couchdb').val();
    var dbName = $('#input_dbname').val();

    for (var record in data['records']) {
        $.ajax({
            url: couchUrlPrefix + "/" + dbName + "/" + data['records'][record]['ID'],
            type: 'put',
            data: JSON.stringify(data['records'][record]),
            dataType: 'json',
            error: function (status) {
                console.log(status);
            }
        });
    }
}

var activateDisambButton = function () {
    if ($('#facetview_freetext').val()) {
        $('#disambiguate').attr("disabled", false);
    }
    else {
        $('#disambiguate').attr("disabled", true);
    }
}

var activateHarvestButton = function () {
    if ($('#facetview_freetext').val()) {
        $('#harvest').attr("disabled", false);
    }
    else {
        $('#harvest').attr("disabled", true);
    }
}