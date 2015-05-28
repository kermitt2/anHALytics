/**
 *  Functions for the anHALytics ElasticSearch front end.
 *
 */

// first define the bind with delay function from (saves loading it separately) 
// https://github.com/bgrins/bindWithDelay/blob/master/bindWithDelay.js

(function ($) {
    $.fn.bindWithDelay = function (type, data, fn, timeout, throttle) {
        var wait = null;
        var that = this;

        if ($.isFunction(data)) {
            throttle = timeout;
            timeout = fn;
            fn = data;
            data = undefined;
        }

        function cb() {
            var e = $.extend(true, {}, arguments[0]);
            var throttler = function () {
                wait = null;
                fn.apply(that, [e]);
            };

            if (!throttle) {
                clearTimeout(wait);
            }
            if (!throttle || !wait) {
                wait = setTimeout(throttler, timeout);
            }
        }

        return this.bind(type, data, cb);
    }
})(jQuery);

// add extension to jQuery with a function to get URL parameters
jQuery.extend({
    getUrlVars: function () {
        var params = new Object
        var hashes = window.location.href.slice(window.location.href.indexOf('?') + 1).split('&')
        for (var i = 0; i < hashes.length; i++) {
            hash = hashes[i].split('=');
            if (hash.length > 1) {
                if (hash[1].replace(/%22/gi, "")[0] == "[" || hash[1].replace(/%22/gi, "")[0] == "{") {
                    hash[1] = hash[1].replace(/^%22/, "").replace(/%22$/, "")
                    var newval = JSON.parse(unescape(hash[1].replace(/%22/gi, '"')))
                } else {
                    var newval = unescape(hash[1].replace(/%22/gi, ""))
                }
                params[hash[0]] = newval
            }
        }
        return params
    },
    getUrlVar: function (name) {
        return jQuery.getUrlVars()[name]
    }
});


// now the display function
(function ($) {
    $.fn.facetview = function (options) {

        // a default value (pulled into options below)
        var resdisplay = [
        ]

        // specify the defaults
        var defaults = {
            "config_file": false, // a remote config file URL
            "facets": [], // facet objects: {"field":"blah", "display":"arg",...}
            "addremovefacets": false, // false if no facets can be added at front end, otherwise list of facet names
            "result_display": resdisplay, // display template for search results
            "display_images": true, // whether or not to display images found in links in search results
            "visualise_filters": true, // whether or not to allow filter vis via d3
            "description": "", // a description of the current search to embed in the display
            "search_url": "", // the URL against which to submit searches
            "search_index": "elasticsearch", // elasticsearch or SOLR
            "default_url_params": {}, // any params that the search URL needs by default
            "freetext_submit_delay": "200", // delay for auto-update of search results
            "query_parameter": "q", // the query parameter if required for setting to the search URL
            "q": "", // default query value
            "predefined_filters": {}, // predefined filters to apply to all searches
            "paging": {
                "from": 0, // where to start the results from
                "size": 10                   // how many results to get
            },
            "mode_query": "simple", // query input, possible values: simple, complex, epoque, nl, semantic, analytics
            "complex_fields": 0, // number of fields introduced in the complex query form
            "collection": "npl"				// by default the collection is considered as scholar litterature
        }

        // and add in any overrides from the call
        // these options are also overridable by URL parameters
        // facetview options are declared as a function so they are available externally
        // (see bottom of this file)
        var provided_options = $.extend(defaults, options)
        var url_options = $.getUrlVars()
        $.fn.facetview.options = $.extend(provided_options, url_options)
        var options = $.fn.facetview.options


        //var fillDefaultColor = '#FF8000';
        var fillDefaultColor = '#BC0E0E';
        //var fillDefaultColor = '#FF9900';
        var fillDefaultColorLight = '#FE9A2E';

        // this is a google key for freebase image service
        var api_key = "AIzaSyBLNMpXpWZxcR9rbjjFQHn_ULbU-w1EZ5U";

        // ===============================================
        // functions to do with filters
        // ===============================================

        // show the filter values
        var showfiltervals = function (event) {
            event.preventDefault();
            if ($(this).hasClass('facetview_open')) {
                $(this).children('i').replaceWith('<i class="icon-plus"></i>')
                $(this).removeClass('facetview_open');
                $('#facetview_' + $(this).attr('rel')).children('li').hide();
            }
            else {
                $(this).children('i').replaceWith('<i class="icon-minus"></i>')
                $(this).addClass('facetview_open');
                $('#facetview_' + $(this).attr('rel')).children('li').show();
            }
        }

        // function to perform for sorting of filters
        var sortfilters = function (event) {
            event.preventDefault()
            var sortwhat = $(this).attr('href')
            var which = 0
            for (item in options.facets) {
                if ('field' in options.facets[item]) {
                    if (options.facets[item]['field'] == sortwhat) {
                        which = item;
                    }
                }
            }
            if ($(this).hasClass('facetview_count')) {
                options.facets[which]['order'] = 'count'
            }
            else if ($(this).hasClass('facetview_term')) {
                options.facets[which]['order'] = 'term'
            }
            else if ($(this).hasClass('facetview_rcount')) {
                options.facets[which]['order'] = 'reverse_count'
            }
            else if ($(this).hasClass('facetview_rterm')) {
                options.facets[which]['order'] = 'reverse_term'
            }
            dosearch();
            if (!$(this).parent().parent().siblings('.facetview_filtershow').hasClass('facetview_open')) {
                $(this).parent().parent().siblings('.facetview_filtershow').trigger('click');
            }
        }

        var editfilter = function (event) {
            event.preventDefault()
            var editwhat = $(this).attr('href');
            var which = $(this).attr('rel');

            var modal = '<div class="modal" id="facetview_editmodal" style="max-width:800px;width:650px;"> \
                <div class="modal-header"> \
                <a class="facetview_removeedit close">×</a> \
                <h3>Edit the facet parameters</h3> \
                </div> \
                <div class="modal-body"> \
				<form class="well">';

            for (truc in options.facets[which]) {
                if (truc == 'type') {
                    modal += '<div class="control-group"> \
					            <label class="control-label" for="select"><b>type</b></label> \
					            <div class="controls"> \
					              <select id="input_type"> \
					                <option';
                    if (options.facets[which]['type'] == 'date') {
                        modal += ' selected ';
                    }
                    modal += '>date</option> \
					                <option';
                    if (options.facets[which]['type'] == 'class') {
                        modal += ' selected ';
                    }
                    modal += '>class</option> \
					                <option';
                    if (options.facets[which]['type'] == 'entity') {
                        modal += ' selected ';
                    }
                    modal += '>entity</option> \
					                <option';
                    if (options.facets[which]['type'] == 'taxonomy') {
                        modal += ' selected ';
                    }
                    modal += '>taxonomy</option> \
					                <option';
                    if (options.facets[which]['type'] == 'country') {
                        modal += ' selected ';
                    }
                    modal += '>country</option> \
					              </select> \
					            </div> \
					          </div>';
                }
                else if (truc == 'view') {
                    modal += '<div class="control-group"> \
					            <label class="control-label" for="select"><b>view</b></label> \
					            <div class="controls"> \
					              <select id="input_type"> \
					                <option';
                    if (options.facets[which]['view'] == 'hidden') {
                        modal += ' selected ';
                    }
                    modal += '>hidden</option> \
					                <option';
                    if (options.facets[which]['view'] == 'graphic') {
                        modal += ' selected ';
                    }
                    modal += '>graphic</option> \
					                <option';
                    if (options.facets[which]['view'] == 'textual') {
                        modal += ' selected ';
                    }
                    modal += '>textual</option> \
					                <option';
                    if (options.facets[which]['view'] == 'all') {
                        modal += ' selected ';
                    }
                    modal += '>all</option> \
					              </select> \
					            </div> \
					          </div>';
                }
                else {
                    modal += '<div class="control-group"> \
						<label class="control-label" for="input"><b>' + truc + '</b></label> \
				 		<div class="controls"> \
						<input type="text" class="input-xxlarge" id="input_' + truc + '" value="'
                            + options.facets[which][truc] + '"/> \
						</div></div>';
                }
            }

            modal += '</form> \
			    </div> \
                <div class="modal-footer"> \
                <a id="facetview_dofacetedit" href="#" class="btn btn-primary" rel="' + which + '">Apply</a> \
                <a class="facetview_removeedit btn close">Cancel</a> \
                </div> \
                </div>';
            $('#facetview').append(modal);
            $('.facetview_removeedit').bind('click', removeedit);
            $('#facetview_dofacetedit').bind('click', dofacetedit);
            $('#facetview_editmodal').modal('show')
        }
        // remove the edit modal from page altogether on close (rebuilt for each filter)
        var removeedit = function (event) {
            event.preventDefault()
            $('#facetview_editmodal').modal('hide')
            $('#facetview_editmodal').remove()
        }
        // update parameters and re-run the facet
        var dofacetedit = function (event) {
            event.preventDefault();
            var which = $(this).attr('rel');

            for (truc in options.facets[which]) {
                options.facets[which][truc] = $(this).parent().parent().find('#input_' + truc).val();
            }

            $('#facetview_editmodal').modal('hide');
            $('#facetview_editmodal').remove();
            options.paging.from = 0;
            buildfilters();
            dosearch();
            //if ( !$(this).parent().parent().siblings('.facetview_filtershow').hasClass('facetview_open') ) {
            //    $(this).parent().parent().siblings('.facetview_filtershow').trigger('click')
            //}
        }

        // adjust how many results are shown
        var morefacetvals = function (event) {
            event.preventDefault()
            var morewhat = options.facets[ $(this).attr('rel') ]
            if ('size' in morewhat) {
                var currentval = morewhat['size'];
            } else {
                var currentval = 6;
            }
            var newmore = prompt('Currently showing ' + currentval +
                    '. How many would you like instead?')
            if (newmore) {
                options.facets[ $(this).attr('rel') ]['size'] = parseInt(newmore);
                $(this).html('show up to (' + newmore + ')')
                dosearch();
                if (!$(this).parent().parent().siblings('.facetview_filtershow').hasClass('facetview_open')) {
                    $(this).parent().parent().siblings('.facetview_filtershow').trigger('click')
                }
            }
        }

        // insert a facet range once selected
        var dofacetrange = function (event) {
            event.preventDefault();
            var rel = $('#facetview_rangerel').html();
            var range = $('#facetview_rangechoices').html();
            var newobj = '<a class="facetview_filterselected facetview_facetrange facetview_clear ' +
                    'btn btn-info" rel="' + rel +
                    '" alt="remove" title="remove"' +
                    ' href="' + $(this).attr("href") + '">' +
                    range + ' <i class="icon-remove"></i></a>';
            $('#facetview_selectedfilters').append(newobj);
            $('.facetview_filterselected').unbind('click', clearfilter);
            $('.facetview_filterselected').bind('click', clearfilter);
            $('#facetview_rangemodal').modal('hide');
            $('#facetview_rangemodal').remove();
            options.paging.from = 0
            dosearch();
        }
        // remove the range modal from page altogether on close (rebuilt for each filter)
        var removerange = function (event) {
            event.preventDefault()
            $('#facetview_rangemodal').modal('hide')
            $('#facetview_rangemodal').remove()
        }
        // build a facet range selector
        var facetrange = function (event) {
            event.preventDefault()
            var modal = '<div class="modal" id="facetview_rangemodal"> \
                <div class="modal-header"> \
                <a class="facetview_removerange close">×</a> \
                <h3>Set a filter range</h3> \
                </div> \
                <div class="modal-body"> \
                <div style=" margin:20px;" id="facetview_slider"></div> \
                <h3 id="facetview_rangechoices" style="text-align:center; margin:10px;"> \
                <span class="facetview_lowrangeval">...</span> \
                <small>to</small> \
                <span class="facetview_highrangeval">...</span></h3> \
                </div> \
                <div class="modal-footer"> \
                <a id="facetview_dofacetrange" href="#" class="btn btn-primary">Apply</a> \
                <a class="facetview_removerange btn close">Cancel</a> \
                </div> \
                </div>';
            $('#facetview').append(modal)
            $('#facetview_rangemodal').append('<div id="facetview_rangerel" style="display:none;">' +
                    $(this).attr('rel') + '</div>')
            $('#facetview_rangemodal').modal('show')
            $('#facetview_dofacetrange').bind('click', dofacetrange)
            $('.facetview_removerange').bind('click', removerange)
            var values = [];
            //var valsobj = $( '#facetview_' + $(this).attr('href').replace(/\./gi,'_') );
            var valsobj = $('#facetview_' + $(this).attr('href'));
            valsobj.children('li').children('a').each(function () {
                var theDate = new Date(parseInt($(this).attr('href')));
                var years = theDate.getFullYear();
                values.push(years);
            })
            values = values.sort();
            $("#facetview_slider").slider({
                range: true,
                min: 0,
                max: values.length - 1,
                values: [0, values.length - 1],
                slide: function (event, ui) {
                    $('#facetview_rangechoices .facetview_lowrangeval').html(values[ ui.values[0] ])
                    $('#facetview_rangechoices .facetview_highrangeval').html(values[ ui.values[1] ])
                }
            })
            $('#facetview_rangechoices .facetview_lowrangeval').html(values[0]);
            $('#facetview_rangechoices .facetview_highrangeval').html(values[ values.length - 1]);
        }


        // pass a list of filters to be displayed
        var buildfilters = function () {
            var filters = options.facets;
            //var thefilters = "<h3>Facets</h3>";
            var thefilters = "";

            for (var idx in filters) {
                var _filterTmpl = ' \
                    <div id="facetview_filterbuttons" class="btn-group"> \
                    <a style="text-align:left; min-width:70%;" class="facetview_filtershow btn" \
                      rel="{{FILTER_NAME}}" href=""> \
                      <!--i class="icon-plus"--></i> \
                      {{FILTER_DISPLAY}}</a> \
                      <a class="btn dropdown-toggle" data-toggle="dropdown" \
                      href="#"><span class="caret"></span></a> \
                      <ul class="dropdown-menu"> \
                        <li><a class="facetview_sort facetview_count" href="{{FILTER_EXACT}}">sort by count</a></li> \
                        <li><a class="facetview_sort facetview_term" href="{{FILTER_EXACT}}">sort by term</a></li> \
                        <li><a class="facetview_sort facetview_rcount" href="{{FILTER_EXACT}}">sort reverse count</a></li> \
                        <li><a class="facetview_sort facetview_rterm" href="{{FILTER_EXACT}}">sort reverse term</a></li> \
                        <li class="divider"></li> \
                        <li><a class="facetview_facetrange" rel="{{FACET_IDX}}" href="{{FILTER_EXACT}}">apply a filter range</a></li>{{FACET_VIS}} \
                        <li><a class="facetview_morefacetvals" rel="{{FACET_IDX}}" href="{{FILTER_EXACT}}">show up to ({{FILTER_HOWMANY}})</a></li> \
                        <li class="divider"></li> \
                        <li><a class="facetview_editfilter" rel="{{FACET_IDX}}" href="{{FILTER_EXACT}}">Edit this filter</a></li> \
                        </ul></div> \
						 <ul id="facetview_{{FILTER_NAME}}" \
                        class="facetview_filters"> \
                    	';
                if (filters[idx]['type'] == 'date') {
                    _filterTmpl +=
                            '<div id="date-input" style="position:relative;margin-top:-15px;margin-bottom:10px;margin-left:-30px;"> \
						   <input type="text" id="day_from" name="day_from" \
						   size="2" style="width: 18px;" placeholder="DD"/> \
						   <input type="text" id="month_from" name="month_from" size="2" \
						   style="width: 22px;" placeholder="MM"/> \
						   <input type="text" id="year_from" name="year_from" size="4" \
						   style="width: 34px;"  placeholder="YYYY"/> \
						   to <input type="text" id="day_to" name="day_to" size="2" \
						   style="width: 18px;" placeholder="DD"" /> \
						   <input type="text" id="month_to" name="month_to" size="2" \
						   style="width: 22px;" placeholder="MM"/> \
					   	   <input type="text" id="year_to" name="year_to" size="4" \
					       style="width: 34px;"  placeholder="YYYY"/> \
					       <div id="validate-date-range" alt="set date range" title="set date range" rel="{{FACET_IDX}}" class="icon-ok" /></div>';
                }
                _filterTmpl += '</ul>';
                if (options.visualise_filters) {
                    var vis = '<li><a class="facetview_visualise" rel="{{FACET_IDX}}" href="{{FILTER_DISPLAY}}">visualise this filter</a></li>';
                    thefilters += _filterTmpl.replace(/{{FACET_VIS}}/g, vis);
                }
                else {
                    thefilters += _filterTmpl.replace(/{{FACET_VIS}}/g, '');
                }
                thefilters = thefilters.replace(/{{FILTER_NAME}}/g, filters[idx]['display'])
                        .replace(/{{FILTER_EXACT}}/g, filters[idx]['display']);

                if ('size' in filters[idx]) {
                    thefilters = thefilters.replace(/{{FILTER_HOWMANY}}/gi, filters[idx]['size']);
                }
                else {
                    // default if size is not indicated in the parameters
                    thefilters = thefilters.replace(/{{FILTER_HOWMANY}}/gi, 6);
                }
                thefilters = thefilters.replace(/{{FACET_IDX}}/gi, idx);
                if ('display' in filters[idx]) {
                    thefilters = thefilters.replace(/{{FILTER_DISPLAY}}/g, filters[idx]['display']);
                } else {
                    thefilters = thefilters.replace(/{{FILTER_DISPLAY}}/g, filters[idx]['field']);
                }
            }

            if (options.search_index != "summon") {
                var temp_intro = '<a style="text-align:left; min-width:20%;margin-bottom:10px;" class="btn" \
	             		id="new_facet" href=""> \
	             		<i class="icon-plus"></i> add new facet </a> \
				';
                $('#facetview_filters').html("").append(temp_intro);
                $('#new_facet').bind('click', add_facet);
            }
            else {
                var temp_intro = '<form class="well" id="scope_area"><label class="checkbox">' +
                        '<input type="checkbox" name="scientific" checked>Technical content</label>';
                temp_intro += '<label class="checkbox">' +
                        '<input type="checkbox" name="fulltext" checked>Full text available online</label>';
                temp_intro += '<label class="checkbox">' +
                        '<input type="checkbox" name="scholarly">Scholarly content</label>';
                //temp_intro += '<button type="button" class="btn" data-toggle="button">Custom scope restriction</button>';
                temp_intro += '</form>';

                $('#facetview_filters').html("").append(temp_intro);
                $('#scope_area').bind('click', setScope);
            }

            $('#facetview_filters').append(thefilters);
            options.visualise_filters ? $('.facetview_visualise').bind('click', show_vis) : "";
            $('.facetview_morefacetvals').bind('click', morefacetvals);
            $('.facetview_facetrange').bind('click', facetrange);
            $('.facetview_sort').bind('click', sortfilters);
            $('.facetview_editfilter').bind('click', editfilter);
            $('.facetview_filtershow').bind('click', showfiltervals);
            options.addremovefacets ? addremovefacets() : "";
            if (options.description) {
                $('#facetview_filters').append('<div><h3>Meta</h3>' + options.description + '</div>');
            }
            $('#validate-date-range').bind('click', setDateRange);
            $('#date-input').hide();
        }

        var setScope = function () {
            if ($('input[name=\"scientific\"]').attr('checked') && (options.scope != 'scientific')) {
                options.scope = 'scientific';
                options.paging.from = 0;
                dosearch();
            }
            else if (!($('input[name=\"scientific\"]').attr('checked')) && (options.scope == 'scientific')) {
                options.scope = null;
                options.paging.from = 0;
                dosearch();
            }
            if (($('input[name=\"fulltext\"]').attr('checked')) && (!options['fulltext'])) {
                options['fulltext'] = true;
                options.paging.from = 0;
                dosearch();
            }
            else if (!($('input[name=\"fulltext\"]').attr('checked')) && (options['fulltext'])) {
                options['fulltext'] = false;
                options.paging.from = 0;
                dosearch();
            }
            if (($('input[name=\"scholarly\"]').attr('checked')) && (!options['scholarly'])) {
                options['scholarly'] = true;
                options.paging.from = 0;
                dosearch();
            }
            else if (!($('input[name=\"scholarly\"]').attr('checked')) && (options['scholarly'])) {
                options['scholarly'] = false;
                options.paging.from = 0;
                dosearch();
            }
        }

        var setDateRange = function () {
            var day_from = 1;
            var month_from = 0;

            var values = [];
            var valsobj = $(this).parent().parent();
            valsobj.children('li').children('a').each(function () {
                var theDate = new Date(parseInt($(this).attr('href')));
                var years = theDate.getFullYear();
                values.push(years);
            })
            //values = values.sort();
            var year_from = values[0];
            if (year_from == 0) {
                year_from = 1;
            }

            var range = "";
            if ($('input[name=\"day_from\"]').val()) {
                day_from = parseInt($('input[name=\"day_from\"]').val());
            }
            if ($('input[name=\"month_from\"]').val()) {
                month_from = parseInt($('input[name=\"month_from\"]').val()) - 1;
            }
            if ($('input[name=\"year_from\"]').val()) {
                year_from = parseInt($('input[name=\"year_from\"]').val());
            }

            var day_to = 1;
            var month_to = 0;
            var year_to = values[values.length - 1];

            if ($('input[name=\"day_to\"]').val()) {
                day_to = parseInt($('input[name=\"day_to\"]').val());
            }
            if ($('input[name=\"month_to\"]').val()) {
                month_to = parseInt($('input[name=\"month_to\"]').val()) - 1;
            }
            if ($('input[name=\"year_to\"]').val()) {
                year_to = parseInt($('input[name=\"year_to\"]').val());
            }

            range += (day_from) + '-' + (month_from + 1) + '-' + year_from;
            range += " to ";
            range += (day_to) + '-' + (month_to + 1) + '-' + year_to;

            var date_from = new Date(year_from, month_from, day_from, 0, 0, 0, 0);
            var date_to = new Date(year_to, month_to, day_to, 0, 0, 0, 0);

            //console.log(date_from.toString('yyyy-MM-dd'));
            //console.log(date_to.toString('yyyy-MM-dd'));

            var rel = $(this).attr('rel');
            var newobj = '<a class="facetview_filterselected facetview_facetrange facetview_clear ' +
                    'btn btn-info" rel="' + rel +
                    '" alt="remove" title="remove"' +
                    ' href="' + date_from.getTime() + '_' + date_to.getTime() + '">' +
                    range + ' <i class="icon-remove"></i></a>';
            $('#facetview_selectedfilters').append(newobj);
            $('.facetview_filterselected').unbind('click', clearfilter);
            $('.facetview_filterselected').bind('click', clearfilter);
            options.paging.from = 0
            dosearch();
        }

        // set the available filter values based on results
        var putvalsinfilters = function (data) {
            // for each filter setup, find the results for it and append them to the relevant filter
            for (var each in options.facets) {
                $('#facetview_' + options.facets[each]['display']).children('li').remove();

                if (options.facets[each]['type'] == 'date') {
                    //console.log(data["facets"][ options.facets[each]['display'] ]);
                    var records = data["facets"][ options.facets[each]['display'] ];
                    for (var item in records) {
                        var itemint = parseInt(item, "10");
                        var theDate = new Date(itemint);
                        var years = theDate.getFullYear();
                        var append = '<li><a class="facetview_filterchoice' +
                                '" rel="' + options.facets[each]['field'] +
                                '" href="' + item + '">' + years +
                                ' (' + addCommas(records[item]) + ')</a></li>';
                        $('#facetview_' + options.facets[each]['display']).append(append);
                    }
                }
                else {
                    var records = data["facets"][ options.facets[each]['display'] ];
                    var numb = 0;
                    for (var item in records) {
                        if (numb >= options.facets[each]['size']) {
                            break;
                        }

                        var item2 = item;
                        if (options.facets[each]['display'].indexOf('class') != -1)
                            item2 = item.replace(/\s/g, '');
                        var append = '<li><a class="facetview_filterchoice' +
                                '" rel="' + options.facets[each]['field'] + '" href="' + item + '">' + item2 +
                                ' (' + addCommas(records[item]) + ')</a></li>';
                        $('#facetview_' + options.facets[each]['display']).append(append);
                        numb++;
                    }
                }
                if (!$('.facetview_filtershow[rel="' + options.facets[each]['display'] +
                        '"]').hasClass('facetview_open')) {
                    $('#facetview_' + options.facets[each]['display']).children("li").hide();
                }
                if ($('#facetview_visualisation_' + options.facets[each]['display']).length > 0) {
                    $('.facetview_visualise[href=' + options.facets[each]['display'] + ']').trigger('click');
                }
            }
            $('.facetview_filterchoice').bind('click', clickfilterchoice);
        }

        // show the add/remove filters options
        var addremovefacet = function (event) {
            event.preventDefault()
            if ($(this).hasClass('facetview_filterexists')) {
                $(this).removeClass('facetview_filterexists');
                delete options.facets[$(this).attr('href')];
            } else {
                $(this).addClass('facetview_filterexists');
                options.facets.push({'field': $(this).attr('title')});
            }
            buildfilters();
            dosearch();
        }
        var showarf = function (event) {
            event.preventDefault()
            $('#facetview_addremovefilters').toggle()
        }
        var addremovefacets = function () {
            $('#facetview_filters').append('<a id="facetview_showarf" href="">' +
                    'add or remove filters</a><div id="facetview_addremovefilters"></div>')
            for (var idx in options.facets) {
                if (options.addremovefacets.indexOf(options.facets[idx].field) == -1) {
                    options.addremovefacets.push(options.facets[idx].field)
                }
            }
            for (var facet in options.addremovefacets) {
                var thisfacet = options.addremovefacets[facet]
                var filter = '<a class="btn '
                var index = 0
                var icon = '<i class="icon-plus"></i>'
                for (var idx in options.facets) {
                    if (options.facets[idx].field == thisfacet) {
                        filter += 'btn-info facetview_filterexists'
                        index = idx
                        icon = '<i class="icon-remove icon-white"></i> '
                    }
                }
                filter += ' facetview_filterchoose" style="margin-top:5px;" href="' + index + '" title="' + thisfacet + '">' + icon + thisfacet + '</a><br />'
                $('#facetview_addremovefilters').append(filter)
            }
            $('#facetview_addremovefilters').hide();
            $('#facetview_showarf').bind('click', showarf);
            $('.facetview_filterchoose').bind('click', addremovefacet);
        }

        // ===============================================
        // filter visualisations
        // ===============================================

        var show_vis = function (event) {
            event.preventDefault();
            var update = false;
            if ($('#facetview_visualisation' + '_' + $(this).attr('href')).length) {
                //$('#facetview_visualisation' + '_'+$(this).attr('href')).remove();
                update = true;
            }

            var vis;
            var indx = null;
            for (var idx in options.facets) {
                if (options.facets[idx]['display'] == $(this).attr('href')) {
                    indx = idx;
                    break;
                }
            }

            if (!update) {
                if ((options.facets[idx]['type'] == 'class') || (options.facets[idx]['type'] == 'country')) {
                    vis = '<div id="facetview_visualisation' + '_' + $(this).attr('href') + '" style="position:relative;top:5px;left:-10px;"> \
	                    <div class="modal-body2" id ="facetview_visualisation' + '_' + $(this).attr('href') + '_chart"> \
	                    </div> \
	                    </div>';
                }
                else if (options.facets[idx]['type'] == 'entity') {
                    vis = '<div id="facetview_visualisation' + '_' + $(this).attr('href') + '" style="position:relative;left:-10px;"> \
	                    <div class="modal-body2" id ="facetview_visualisation' + '_' + $(this).attr('href') + '_chart"> \
	                    </div> \
	                    </div>';
                }
                else if (options.facets[idx]['type'] == 'taxonomy') {
                    vis = '<div id="facetview_visualisation' + '_' + $(this).attr('href') + '" style="position:relative;top:5px;left:-15px"> \
	                    <div class="modal-body2" id ="facetview_visualisation' + '_' + $(this).attr('href') + '_chart"> \
	                    </div> \
	                    </div>';
                }
                else {
                    vis = '<div id="facetview_visualisation' + '_' + $(this).attr('href') + '" style="position:relative;left:-10px;"> \
	                    <div class="modal-body2" id ="facetview_visualisation' + '_' + $(this).attr('href') + '_chart" style="position:relative;left:-18px;"> \
	                    </div> \
	                    </div>';
                }
                vis = vis.replace(/{{VIS_TITLE}}/gi, $(this).attr('href'));
                $('#facetview_' + $(this).attr('href')).prepend(vis);
            }
            var parentWidth = $('#facetview_filters').width();

            if ((options.facets[idx]['type'] == 'class') || (options.facets[idx]['type'] == 'country')) {
                donut2($(this).attr('rel'), $(this).attr('href'),
                        parentWidth * 0.8, 'facetview_visualisation' + '_' + $(this).attr('href') + "_chart", update);
            }
            else if (options.facets[idx]['type'] == 'date') {
                timeline($(this).attr('rel'), parentWidth * 0.75,
                        'facetview_visualisation' + '_' + $(this).attr('href') + "_chart");
                $('#date-input').show();
            }
            else if (options.facets[idx]['type'] == 'taxonomy') {
                wheel($(this).attr('rel'), $(this).attr('href'), parentWidth * 0.8,
                        'facetview_visualisation' + '_' + $(this).attr('href') + "_chart", update);
            }
            else {
                bubble($(this).attr('rel'), parentWidth * 0.8,
                        'facetview_visualisation' + '_' + $(this).attr('href') + "_chart", update);
            }

        }

        var wheel = function (facetidx, facetkey, width, place, update) {
            var w = width,
                    h = w,
                    r = w / 2,
                    x = d3.scale.linear().range([0, 2 * Math.PI]),
                    y = d3.scale.pow().exponent(1.3).domain([0, 1]).range([0, r]),
                    p = 5,
                    duration = 1000;

            var vis = d3.select("#facetview_visualisation_" + facetkey + " > .modal-body2");
            if (update) {
                vis.select("svg").remove();
            }

            vis = d3.select("#facetview_visualisation_" + facetkey + " > .modal-body2").append("svg:svg")
                    .attr("width", w + p * 2)
                    .attr("height", h + p * 2)
                    .append("g")
                    .attr("transform", "translate(" + (r + p) + "," + (r + p) + ")");

            var partition = d3.layout.partition()
                    .sort(null)
                    .value(function (d) {
                        return 5.8 - d.depth;
                    });

            var arc = d3.svg.arc()
                    .startAngle(function (d) {
                        return Math.max(0, Math.min(2 * Math.PI, x(d.x)));
                    })
                    .endAngle(function (d) {
                        return Math.max(0, Math.min(2 * Math.PI, x(d.x + d.dx)));
                    })
                    .innerRadius(function (d) {
                        return Math.max(0, d.y ? y(d.y) : d.y);
                    })
                    .outerRadius(function (d) {
                        return Math.max(0, y(d.y + d.dy));
                    });

            //var fill = d3.scale.log(.1, 1).domain([0.005,0.1]).range(["#FF7700","#FCE6D4"]);
            var fill = d3.scale.log(.1, 1).domain([0.005, 0.1]).range(["#FCE6D4", "#FF7700"]);

            var facetfield = options.facets[facetidx]['field'];
            var records = options.data['facets'][facetkey];
            var datas = [];
            var sum = 0;
            var numb = 0;
            for (var item in records) {
                if (numb >= options.facets[facetidx]['size']) {
                    break;
                }
                var item2 = item.replace(/\s/g, '');
                var count = records[item];
                sum += count;

                var ind = item2.indexOf(".");
                if (ind != -1) {
                    // first level
                    var item3 = item2.substring(0, ind);
                    var found3 = false;
                    for (var p in datas) {
                        if (datas[p].term == item3) {
                            datas[p]['count'] += records[item];
                            found3 = true;
                            break;
                        }
                    }
                    if (!found3) {
                        datas.push({'term': item3, 'count': records[item], 'source': item, 'relCount': 0});
                    }
                    var ind2 = item2.indexOf(".", ind + 1);
                    if (ind2 != -1) {
                        //second level
                        var item4 = item2.substring(0, ind2);
                        var found4 = false;
                        for (var p in datas) {
                            if (datas[p].term == item4) {
                                datas[p]['count'] += records[item];
                                found4 = true;
                                break;
                            }
                        }
                        if (!found4) {
                            datas.push({'term': item4, 'count': records[item], 'source': item, 'relCount': 0});
                        }
                        datas.push({'term': item2, 'count': records[item], 'source': item, 'relCount': 0});
                    }
                    else {
                        var found3 = false;
                        for (var p in datas) {
                            if (datas[p].term == item3) {
                                datas[p]['count'] += records[item];
                                found3 = true;
                                break;
                            }
                        }
                        if (!found3) {
                            datas.push({'term': item3, 'count': records[item], 'source': item, 'relCount': 0});
                        }
                        datas.push({'term': item2, 'count': records[item], 'source': item, 'relCount': 0});
                    }
                }
                else {
                    var found2 = false;
                    for (var p in datas) {
                        if (datas[p].term == item2) {
                            datas[p]['count'] += records[item];
                            found2 = true;
                            break;
                        }
                    }
                    if (!found2) {
                        datas.push({'term': item2, 'count': records[item], 'source': item, 'relCount': 0});
                    }
                }
                numb++;
            }
            //console.log('wheel data:');			
            //console.log(datas);
            for (var item in datas) {
                datas[item]['relCount'] = datas[item]['count'] / sum;
            }

            //var entries = datas.sort( function(a, b) { return a.count > b.count ? -1 : 1; } );
            //var entries = datas.sort( function(a, b) { return a.name > b.name ? -1 : 1; } );
            var entries = datas;
            /*var data0 = [];
             for(var item in entries) {
             data0.push(entries[item]['count']);
             }*/

            var json = [];

            // first level
            for (var item in entries) {
                var symbol = entries[item]['term'];
                var ind = symbol.indexOf(".");
                if (ind == -1) {
                    //first level category
                    json.push({'name': symbol, 'colour': fill(entries[item]['relCount'])});
                }
            }

            //second level
            for (var item in entries) {
                //var ind = entries[item]['term'].indexOf(":");
                var ind = entries[item]['term'].indexOf(".");
                if (ind != -1) {
                    var symbol = entries[item]['term'];
                    var motherCategory = symbol.substring(0, ind);
                    for (item2 in json) {
                        if (json[item2]['name'] == motherCategory) {
                            // second level category
                            var children = [];
                            if (json[item2]['children']) {
                                children = json[item2]['children'];
                            }
                            var newSymbol = symbol.substring(ind + 1, symbol.length);
                            var ind2 = newSymbol.indexOf(".");
                            if (ind2 == -1) {
                                children.push({'name': newSymbol,
                                    'colour': fill(entries[item]['relCount'])});
                                json[item2]['children'] = children;
                            }
                            break;
                        }
                    }
                }
            }

            // third and last level
            for (var item in entries) {
                var ind = entries[item]['term'].indexOf(".");
                if (ind != -1) {
                    var symbol = entries[item]['term'];
                    var motherCategory = symbol.substring(0, ind);
                    for (item2 in json) {
                        if (json[item2]['name'] == motherCategory) {
                            var newSymbol = symbol.substring(ind + 1, symbol.length);
                            //var ind2 = newSymbol.indexOf(":");
                            var ind2 = newSymbol.indexOf(".");
                            if (ind2 != -1) {
                                var motherCategory2 = newSymbol.substring(0, ind2);
                                for (item3 in json[item2]['children']) {
                                    if (json[item2]['children'][item3]['name'] == motherCategory2) {
                                        // third level category (and last one)
                                        var children2 = [];
                                        if (json[item2]['children'][item3]['children']) {
                                            children2 = json[item2]['children'][item3]['children']
                                        }
                                        children2.push({'name': newSymbol.substring(ind2 + 1, newSymbol.length),
                                            'colour': fill(entries[item]['relCount'])});
                                        json[item2]['children'][item3]['children'] = children2;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }

            //console.log(json);
            //console.log(JSON.stringify(json,null, 2));

            var nodes = partition.nodes({children: json});
            var path = vis.selectAll("path")
                    .data(nodes);
            path.enter().append("path")
                    .attr("id", function (d, i) {
                        return "path-" + i;
                    })
                    .attr("d", arc)
                    .attr("fill-rule", "evenodd")
                    .style("fill", colour)
                    //.style("fill", function(d) { return fill(d.data); })
                    .on("click", click);

            var text = vis.selectAll("text").data(nodes);
            var textEnter = text.enter().append("text")
                    .style("fill-opacity", 1)
                    .style("fill", function (d) {
                        return brightness(d3.rgb(colour(d))) < 125 ? "#eee" : "#000";
                    })
                    .attr("text-anchor", function (d) {
                        return x(d.x + d.dx / 2) > Math.PI ? "end" : "start";
                    })
                    .attr("dy", ".2em")
                    .attr("transform", function (d) {
                        var multiline = (d.name || "").split(" ").length > 1,
                                angle = x(d.x + d.dx / 2) * 180 / Math.PI - 90,
                                rotate = angle + (multiline ? -.5 : 0);
                        return "rotate(" + rotate + ")translate(" + (y(d.y) + p) + ")rotate(" + (angle > 90 ? -180 : 0) + ")";
                    })
                    .on("click", click);
            textEnter.append("tspan")
                    .attr("x", 0)
                    .text(function (d) {
                        return d.depth ? d.name.split(" ")[0] : "";
                    });
            textEnter.append("tspan")
                    .attr("x", 0)
                    .attr("dy", "1em")
                    .text(function (d) {
                        return d.depth ? d.name.split(" ")[1] || "" : "";
                    });


            function click(d) {
                // we need to reconstitute the complete field name
                var theName = d.name;
                if (d.parent && d.parent.name) {
                    //theName = d.parent.name + ":" + theName;
                    theName = d.parent.name + "." + theName;
                    if (d.parent.parent && d.parent.parent.name) {
                        //theName = d.parent.parent.name + ":" + theName;
                        theName = d.parent.parent.name + "." + theName;
                    }
                }

                clickGraph(facetfield, theName, theName);

                path.transition()
                        .duration(duration)
                        .attrTween("d", arcTween(d));

                // Somewhat of a hack as we rely on arcTween updating the scales.
                text
                        .style("visibility", function (e) {
                            return isParentOf(d, e) ? null : d3.select(this).style("visibility");
                        })
                        .transition().duration(duration)
                        .attrTween("text-anchor", function (d) {
                            return function () {
                                return x(d.x + d.dx / 2) > Math.PI ? "end" : "start";
                            };
                        })
                        .attrTween("transform", function (d) {
                            var multiline = (d.name || "").split(" ").length > 1;
                            return function () {
                                var angle = x(d.x + d.dx / 2) * 180 / Math.PI - 90,
                                        rotate = angle + (multiline ? -.5 : 0);
                                return "rotate(" + rotate + ")translate(" + (y(d.y) + p) + ")rotate(" + (angle > 90 ? -180 : 0) + ")";
                            };
                        })
                        .style("fill-opacity", function (e) {
                            return isParentOf(d, e) ? 1 : 1e-6;
                        })
                        .each("end", function (e) {
                            d3.select(this).style("visibility", isParentOf(d, e) ? null : "hidden");
                        });
            }

            function isParentOf(p, c) {
                if (p === c)
                    return true;
                if (p.children) {
                    return p.children.some(function (d) {
                        return isParentOf(d, c);
                    });
                }
                return false;
            }

            function colour(d) {
                if (d.children) {
                    // There is a maximum of two children!
                    var colours = d.children.map(colour),
                            a = d3.hsl(colours[0]),
                            b = d3.hsl(colours[1]);
                    // L*a*b* might be better here...
                    return d3.hsl((a.h + b.h) / 2, a.s * 1.2, a.l / 1.2);
                }
                return d.colour || "#fff";
            }

            // Interpolate the scales!
            function arcTween(d) {
                var my = maxY(d),
                        xd = d3.interpolate(x.domain(), [d.x, d.x + d.dx]),
                        yd = d3.interpolate(y.domain(), [d.y, my]),
                        yr = d3.interpolate(y.range(), [d.y ? 20 : 0, r]);
                return function (d) {
                    return function (t) {
                        x.domain(xd(t));
                        y.domain(yd(t)).range(yr(t));
                        return arc(d);
                    };
                };
            }

            function maxY(d) {
                return d.children ? Math.max.apply(Math, d.children.map(maxY)) : d.y + d.dy;
            }

            // http://www.w3.org/WAI/ER/WD-AERT/#color-contrast
            function brightness(rgb) {
                return rgb.r * .299 + rgb.g * .587 + rgb.b * .114;

            }
        }

        var donut = function (facetidx, facetkey, width, place) {
            var facetfield = options.facets[facetidx]['field'];
            var records = options.data['facets'][facetkey];

            if (records.length == 0) {
                $('#' + place).hide();
                return;
            }
            else {
                var siz = 0;
                for (var item in records) {
                    siz++;
                }
                if (siz == 0) {
                    $('#' + place).hide();
                    return;
                }
            }
            $('#' + place).show();

            options.data.facets2[facetkey] = [];
            var numb = 0;
            for (var item in records) {
                if (numb >= options.facets[facetidx]['size']) {
                    break;
                }
                var item2 = item.replace(/\s/g, '');
                options.data.facets2[facetkey].push({'term': item2, 'count': records[item], 'source': item});
                numb++;
            }

            var data = options.data.facets2[facetkey];

            var entries = data.sort(function (a, b) {
                return a.term < b.term ? -1 : 1;
            }),
                    // Create an array holding just the values (counts)
                    values = pv.map(entries, function (e) {
                        return e.count;
                    });

            // Set-up dimensions and color scheme for the chart
            var w = width,
                    h = width * 0.75;

            // Create the basis panel
            var vis = new pv.Panel()
                    .width(w)
                    .height(h)
                    .margin(5, 0, 0, 0);

            // Create the "wedges" of the chart
            vis.add(pv.Wedge)
                    // Set-up auxiliary variable to hold state (mouse over / out)
                    .def("active", -1)
                    // Pass the normalized data to Protovis
                    .data(pv.normalize(values))
                    // Set-up chart position and dimension
                    .left(w / 2.6)
                    .top(w / 2.6)
                    .outerRadius(w / 2.6)
                    // Create a "donut hole" in the center of the chart
                    .innerRadius(15)
                    // Compute the "width" of the wedge
                    .angle(function (d) {
                        return d * 2 * Math.PI;
                    })
                    .fillStyle(pv.Scale.log(.1, 1).range("#FF7700", "#FCE6D4"))
                    // Add white stroke
                    .strokeStyle("#fff")
                    .event("mousedown", function (d) {
                        var term = entries[this.index].term;
                        var source = entries[this.index].source;
                        if (source)
                            clickGraph(facetfield, term, source);
                        else
                            clickGraph(facetfield, term, term);
                        //return (alert("Filter the results by '"+term+"'"));
                    })
                    .anchor("center")
                    .add(pv.Label)
                    .data(entries)
                    .text(function (d) {
                        return d.term;
                    })
                    .textAngle(0)
                    .textStyle("black")
                    .font("09pt sans-serif")

                    // Bind the chart to DOM element
                    .root.canvas(place)
                    // And render it.
                    .render();
        }


        var donut2 = function (facetidx, facetkey, width, place, update) {
            var vis = d3.select("#facetview_visualisation_" + facetkey + " > .modal-body2");

            var facetfield = options.facets[facetidx]['field'];
            var records = options.data['facets'][facetkey];
            if (records.length == 0) {
                $('#' + place).hide();
                return;
            }
            else {
                var siz = 0;
                for (var item in records) {
                    siz++;
                }
                if (siz == 0) {
                    $('#' + place).hide();
                    return;
                }
            }
            $('#' + place).show();

            var data2 = [];
            var sum = 0;
            var numb = 0;
            for (var item in records) {
                if (records[item] > 0) {
                    if (facetkey == 'Discipline') {
                        if (options.search_index == 'summon') {
                            if (options.scope == 'scientific') {
                                //console.log(item);
                                if (scientificDisciplinesLow.indexOf(item) == -1) {
                                    continue;
                                }
                            }
                        }
                    }

                    if (numb >= options.facets[facetidx]['size']) {
                        break;
                    }

                    var item2 = item.replace(/\s/g, '');
                    var count = records[item];
                    sum += count;
                    data2.push({'term': item2, 'count': records[item], 'source': item, 'relCount': 0});
                    numb++;
                }
            }

            for (var item in data2) {
                if (data2[item]['count'] > 0) {
                    data2[item]['relCount'] = data2[item]['count'] / sum;
                }
            }

            var data = data2;
            var entries = data.sort(function (a, b) {
                return a.count < b.count ? -1 : 1;
            });

            var data0 = [];
            for (var item in entries) {
                data0.push(entries[item]['relCount']);
            }

            var height = width * 0.75,
                    outerRadius = Math.min(width, height) / 2,
                    innerRadius = outerRadius * .2,
                    n = data0.length,
                    q = 0,
                    //color = d3.scale.log(.1, 1).range(["#FF7700","#FCE6D4"]),
                    //color = d3.scale.log(.01, .9).range(["#FF7700","#FCE6D4"]),
                    arc = d3.svg.arc(),
                    //fill = d3.scale.log(.1, 1).domain([0.1,0.9]).range(["#FF7700","#FCE6D4"]);
                    fill = d3.scale.log(.1, 1).domain([0.1, 0.9]).range([fillDefaultColor, fillDefaultColorLight]);
            fillDefaultColor

            //fill = d3.scale.log(.1, 1).range(["#FF7700","#FCE6D4"]),
            donute = d3.layout.pie().sort(null);

            if (update) {
                vis.selectAll("svg").remove();
            }

            var data_arc = arcs(data0);
            vis.append("svg:svg")
                    .attr("width", width)
                    .attr("height", height)
                    .selectAll("g.arc")
                    //.data(arcs(data0))
                    .data(data_arc)
                    .enter()
                    .append("g")
                    .attr("class", "arc")
                    .attr("transform", "translate(" + (outerRadius * 1.3) + "," + outerRadius + ")")
                    .attr("index", function (d) {
                        return d.index;
                    })
                    .on("mousedown", function (d) {
                        var index = this.getAttribute("index")
                        var term = entries[index].term;
                        var source = entries[index].source;
                        if (source)
                            clickGraph(facetfield, term, source);
                        else
                            clickGraph(facetfield, term, term);
                    })
                    .append("path")
                    //.attr("fill", function(d, i) { return color(entries[i]['relCount']); })
                    .style("fill", function (d) {
                        return fill(d.data);
                    })
                    .attr("stroke", "#fff")
                    .attr("d", arc);

            /*	vis.selectAll("g")
             .append("svg:text")
             .attr("transform", function(d) {                  
             d.innerRadius = innerRadius;
             d.outerRadius = outerRadius*1.30;
             return "translate(" + arc.centroid(d) + ")";  
             })
             .attr('text-anchor', 'middle')
             .text(function(d) { return d.term;} )
             .style("textStyle", "black")
             .style("font", "09pt sans-serif");*/

            // we need to re-create all the arcs for placing the text labels, so that the labels
            // are not covered by the arcs
            // we also enlarge the svg area for the labels so that they are not cut
            var text = vis.select("svg")
                    .append("svg:svg")
                    .attr("width", width * 1.2)
                    .attr("height", height * 1.2)
                    .selectAll("g")
                    .data(data_arc)
                    .enter()
                    .append("g")
                    .attr("class", "arc")
                    .attr("transform", "translate(" + (outerRadius * 1.30) + "," + outerRadius + ")")
                    .append("text")
                    .attr("class", "labels")
                    .attr("transform", function (d) {
                        d.innerRadius = innerRadius;
                        d.outerRadius = outerRadius * 1.30;
                        return "translate(" + arc.centroid(d) + ")";
                    })
                    .attr('text-anchor', 'middle')
                    .text(function (d) {
                        return d.term
                    })
                    .style("textStyle", "black")
                    .style("font", "09pt sans-serif")
                    .attr("index", function (d) {
                        return d.index;
                    })
                    .on("mousedown", function (d) {
                        var index = this.getAttribute("index")
                        var term = entries[index].term;
                        var source = entries[index].source;
                        if (source)
                            clickGraph(facetfield, term, source);
                        else
                            clickGraph(facetfield, term, term);
                    });

            // Store the currently-displayed angles in this._current.
            // Then, interpolate from this._current to the new angles.
            function arcTween(a) {
                var i = d3.interpolate(this._current, a);
                this._current = i(0);
                return function (t) {
                    return arc(i(t));
                };
            }

            function arcs(data0) {
                var arcs0 = donute(data0),
                        i = -1,
                        arc;
                while (++i < n) {
                    arc = arcs0[i];
                    arc.innerRadius = innerRadius;
                    arc.outerRadius = outerRadius;
                    arc.next = arcs0[i];
                    arc.term = entries[i]['term'];
                    arc.index = i;
                }
                return arcs0;
            }

            function swap() {
                d3.selectAll("g.arc > path")
                        .data(++q & 1 ? arcs(data0, data1) : arcs(data1, data0))
                        .each(transitionSplit);
            }

            // 1. Wedges split into two rings.
            function transitionSplit(d, i) {
                d3.select(this)
                        .transition().duration(1000)
                        .attrTween("d", tweenArc({
                            innerRadius: i & 1 ? innerRadius : (innerRadius + outerRadius) / 2,
                            outerRadius: i & 1 ? (innerRadius + outerRadius) / 2 : outerRadius
                        }))
                        .each("end", transitionRotate);
            }

            // 2. Wedges translate to be centered on their final position.
            function transitionRotate(d, i) {
                var a0 = d.next.startAngle + d.next.endAngle,
                        a1 = d.startAngle - d.endAngle;
                d3.select(this)
                        .transition().duration(1000)
                        .attrTween("d", tweenArc({
                            startAngle: (a0 + a1) / 2,
                            endAngle: (a0 - a1) / 2
                        }))
                        .each("end", transitionResize);
            }

            // 3. Wedges then update their values, changing size.
            function transitionResize(d, i) {
                d3.select(this)
                        .transition().duration(1000)
                        .attrTween("d", tweenArc({
                            startAngle: d.next.startAngle,
                            endAngle: d.next.endAngle
                        }))
                        .each("end", transitionUnite);
            }

            // 4. Wedges reunite into a single ring.
            function transitionUnite(d, i) {
                d3.select(this)
                        .transition().duration(1000)
                        .attrTween("d", tweenArc({
                            innerRadius: innerRadius,
                            outerRadius: outerRadius
                        }));
            }

            function tweenArc(b) {
                return function (a) {
                    var i = d3.interpolate(a, b);
                    for (var key in b)
                        a[key] = b[key]; // update data
                    return function (t) {
                        return arc(i(t));
                    };
                };
            }
        }

        var timeline = function (facetidx, width, place) {
            var facetkey = options.facets[facetidx]['display'];
            var facetfield = options.facets[facetidx]['field'];

            // Set-up the data
            var entries = options.data.facets3[facetkey];
            // Add the last "blank" entry for proper timeline ending
            if (entries.length > 0) {
                //if (entries.length == 1) {	
                entries.push({count: entries[entries.length - 1].count});
            }

            // Set-up dimensions and scales for the chart
            var w = 250,
                    h = 80,
                    max = pv.max(entries, function (d) {
                        return d.count;
                    }),
                    x = pv.Scale.linear(0, entries.length - 1).range(0, w),
                    y = pv.Scale.linear(0, max).range(0, h);

            // Create the basis panel
            var vis = new pv.Panel()
                    .width(w)
                    .height(h)
                    .bottom(40)
                    .left(0)
                    .right(0)
                    .top(3);

            // Add the X-ticks
            vis.add(pv.Rule)
                    .data(entries)
                    .visible(function (d) {
                        return d.time;
                    })
                    .left(function () {
                        return x(this.index);
                    })
                    .bottom(-15)
                    .height(15)
                    .strokeStyle("#33A3E1")
                    // Add the tick label
                    .anchor("right").add(pv.Label)
                    .text(function (d) {
                        var date = new Date(parseInt(d.time));
                        var year = date.getYear();
                        if (year >= 100) {
                            year = year - 100;
                        }
                        if (year == 0) {
                            year = '00';
                        }
                        else if (year < 10) {
                            year = '0' + year;
                        }
                        return year;
                    })
                    .textStyle("#333333")
                    .textMargin("2")

            // Add container panel for the chart
            vis.add(pv.Panel)
                    // Add the area segments for each entry
                    .add(pv.Area)
                    // Set-up auxiliary variable to hold state (mouse over / out) 
                    .def("active", -1)
                    // Pass the data to Protovis
                    .data(entries)
                    .bottom(0)
                    // Compute x-axis based on scale
                    .left(function (d) {
                        return x(this.index);
                    })
                    // Compute y-axis based on scale
                    .height(function (d) {
                        return y(d.count);
                    })
                    // Make the chart curve smooth
                    .interpolate('cardinal')
                    // Divide the chart into "segments" (needed for interactivity)
                    .segmented(true)
                    .strokeStyle("#fff")
                    .fillStyle(fillDefaultColorLight)

                    // On "mouse down", perform action, such as filtering the results...
                    .event("mousedown", function (d) {
                        var time = entries[this.index].time;
                        var date = new Date(parseInt(time));
                        clickGraph(facetfield, date.getFullYear(), time);
                    })

                    // Add thick stroke to the chart
                    .anchor("top").add(pv.Line)
                    .lineWidth(3)
                    .strokeStyle(fillDefaultColor)

                    // Bind the chart to DOM element
                    .root.canvas(place)
                    // And render it.
                    .render();
        }

        var bubble = function (facetidx, width, place, update) {
            var facetkey = options.facets[facetidx]['display'];
            var facetfield = options.facets[facetidx]['field'];
            var facets = options.data['facets'][facetkey];
            if (facets.length == 0) {
                $('#' + place).hide();
                return;
            }
            else {
                var siz = 0;
                for (var item in facets) {
                    siz++;
                }
                if (siz == 0) {
                    $('#' + place).hide();
                    return;
                }
            }
            $('#' + place).show();

            var data = {"children": []};
            var count = 0;
            var numb = 0;
            for (var fct in facets) {
                if (numb >= options.facets[facetidx]['size']) {
                    break;
                }

                var arr = {
                    "className": fct,
                    "packageName": count++,
                    "value": facets[fct]
                }
                data["children"].push(arr);
                numb++;
            }

            var r = width,
                    format = d3.format(",d"),
                    fill = d3.scale.linear().domain([0, 5]).range(["#FF7700", "#FCE6D4"]);
            var bubblee = d3.layout.pack()
                    .sort(null)
                    .size([r, r]);

            var vis = d3.select("#facetview_visualisation_" + facetkey + " > .modal-body2");
            if (update) {
                vis.selectAll("svg").remove();
            }

            vis = d3.select("#facetview_visualisation_" + facetkey + " > .modal-body2").append("svg:svg")
                    .attr("width", r * 1.5)
                    .attr("height", r)
                    .attr("class", "bubble");
            var node = vis.selectAll("g.node")
                    .data(bubblee(data)
                            .filter(function (d) {
                                return !d.children;
                            }))
                    .enter().append("svg:g")
                    .attr("class", "node")
                    .attr("transform", function (d) {
                        return "translate(" + d.x + "," + d.y + ")";
                    });
            node.append("svg:title")
                    .text(function (d) {
                        if (d.data)
                            return d.data.className + ": " + format(d.data.value);
                        else
                            return d.className + ": " + format(d.value);
                    });
            node.append("svg:circle")
                    .attr("r", function (d) {
                        return d.r;
                    })
                    .style("fill", function (d) {
                        if (d.data)
                            return fill(d.data.packageName);
                        else
                            return fill(d.packageName);
                    });
            node.on('click', function (d) {
                if (d.data)
                    clickGraph(facetfield, d.data.className, d.data.className);
                else
                    clickGraph(facetfield, d.className, d.className);
            });

            var vis2 = d3.select("#facetview_visualisation_" + facetkey + " > .modal-body2").select("svg")
                    .append("svg")
                    .attr("width", r * 1.5)
                    .attr("height", r)
                    .selectAll("g.node")
                    .data(bubblee(data)
                            .filter(function (d) {
                                return !d.children;
                            }))
                    .enter().append("svg:g")
                    .attr("class", "node")
                    .attr("transform", function (d) {
                        return "translate(" + d.x + "," + d.y + ")";
                    })
                    .append("svg:text")
                    .attr("text-anchor", "middle")
                    .attr("dy", ".3em")
                    .text(function (d) {
                        if (d.data && d.data.className)
                            return d.data.className.substr(0, 10) + ".. (" + d.data.value + ")";
                        else if (d.className)
                            return d.className.substr(0, 10) + ".. (" + d.value + ")";
                    })
                    .on("mousedown", function (d) {
                        if (d.data)
                            clickGraph(facetfield, d.data.className, d.data.className);
                        else
                            clickGraph(facetfield, d.className, d.className);
                    });
        };

        // normal click on a graphical facet
        var clickGraph = function (facetKey, facetValueDisplay, facetValue) {
            var newobj = '<a class="facetview_filterselected facetview_clear ' +
                    'btn btn-info" rel="' + facetKey +
                    '" alt="remove" title="remove"' +
                    ' href="' + facetValue + '">' +
                    facetValueDisplay + ' <i class="icon-remove"></i></a>'
            $('#facetview_selectedfilters').append(newobj)
            $('.facetview_filterselected').unbind('click', clearfilter)
            $('.facetview_filterselected').bind('click', clearfilter)
            options.paging.from = 0;
            dosearch();
            //$('#facetview_visualisation'+"_"+facetkey).remove();
        }

        // ===============================================
        // building results
        // ===============================================

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

        // decrement result set
        var decrement = function (event) {
            event.preventDefault();
            if ($(this).html() != '..') {
                options.paging.from = options.paging.from - options.paging.size;
                options.paging.from < 0 ? options.paging.from = 0 : "";
                dosearch();
            }
        }

        // increment result set
        var increment = function (event) {
            event.preventDefault()
            if ($(this).html() != '..') {
                options.paging.from = parseInt($(this).attr('href'));
                dosearch();
            }
        }

        function addCommas(nStr) {
            nStr += '';
            x = nStr.split('.');
            x1 = x[0];
            x2 = x.length > 1 ? '.' + x[1] : '';
            var rgx = /(\d+)(\d{3})/;
            while (rgx.test(x1)) {
                x1 = x1.replace(rgx, '$1' + ',' + '$2');
            }
            return x1 + x2;
        }

        // write the metadata to the page
        var putmetadata = function (data) {
            $('#results_summary').empty();

            $('#results_summary').append("<p style='color:grey;'>"
                    + addCommas("" + data.found) + " results - in " + Math.floor(data.took)
                    + " ms (server time)</p>");

            if (options.search_index == "summon") {
                // did you mean suggestion
                var suggest = data.suggest;
                if (suggest.length > 0) {
                    var candidate = suggest[0]['suggestedQuery'];
                    $('#results_summary')
                            .append("<p>Did you mean: <a href='#' onclick='document.getElementById(\"facetview_freetext\").value=\"" +
                                    candidate +
                                    "\"; $(\"#facetview_freetext\").focus().trigger(\"keyup\");' id='suggest_token'><span style='color:" + fillDefaultColor + ";'><strong>"
                                    + candidate + "</strong></span></a> ?</p>");
                }
            }

            if (typeof (options.paging.from) != 'number') {
                options.paging.from = parseInt(options.paging.from);
            }
            if (typeof (options.paging.size) != 'number') {
                options.paging.size = parseInt(options.paging.size);
            }

            var metaTmpl = ' \
              <div class="pagination"> \
                <ul> \
                  <li class="prev"><a id="facetview_decrement" href="{{from}}">&laquo; back</a></li> \
                  <li class="active"><a>{{from}} &ndash; {{to}} of {{total}}</a></li> \
                  <li class="next"><a id="facetview_increment" href="{{to}}">next &raquo;</a></li> \
                </ul> \
              </div> \
              ';

            if (options['mode_query'] == 'nl') {
                metaTmpl += ' <div class="span4">&nbsp;</div> \
			   ';
            }

            $('#facetview_metadata').html("Not found...");
            if (data.found) {
                var from = options.paging.from + 1;
                var size = options.paging.size;
                !size ? size = 10 : "";
                var to = options.paging.from + size;
                data.found < to ? to = data.found : "";
                var meta = metaTmpl.replace(/{{from}}/g, from);
                meta = meta.replace(/{{to}}/g, to);
                meta = meta.replace(/{{total}}/g, addCommas("" + data.found));
                $('#facetview_metadata').html("").append(meta);
                $('#facetview_decrement').bind('click', decrement)
                from < size ? $('#facetview_decrement').html('..') : ""
                $('#facetview_increment').bind('click', increment)
                data.found <= to ? $('#facetview_increment').html('..') : ""
            }
        }

        // given a result record, build how it should look on the page
        var buildrecord = function (index, node) {
            var record = options.data['records'][index];
            var highlight = options.data['highlights'][index];
            var score = options.data['scores'][index];

            var result = '';

            var jsonObject = eval(record);
            //var jsonObject = record;

//			result += '<tr><td>';
            result += '<tr style="border-collapse:collapse;"><td>';

            //result += '<table class="table" cellspacing="0" cellpadding="0" style="width:100%;border-collapse:collapse;border:none;">'+
            //	'<tr style="width:100%;border-collapse:collapse;border:none;">';

            result += '<div class="row-fluid">';

            var type = null;
            var id = null;
            if (options.search_index == "summon") {
                //result += '<td style="width:48px;">'
                result += '<div class="span1">';
                type = jsonObject['ContentType'];
                var openurl = jsonObject['openUrl'];

                if (type) {
                    if (type.length > 0) {
                        if ((type[0] == "Journal Article") || (type[0] == "Trade Publication Article")) {
                            result += '<img  style="float:center; width:38px; '
                                    + 'margin:0 0px 0px 0; max-height:64px;" src="data/images/article.png" />';
                        }
                        else if ((type[0] == "Book") || (type[0] == "eBook")) {
                            result += '<img  style="float:center; width:28px; '
                                    + 'margin-left:4px; max-height:64px;" src="data/images/book1.png" />';
                        }
                        else if (type[0] == "Standard") {
                            result += '<img style="float:center; width:38px; '
                                    + 'margin:0 5px 10px 0; max-height:64px;" src="data/images/standard.png" />';
                        }
                        else if ((type[0] == "Newspaper Article") || (type[0] == "Newsletter")) {
                            result += '<img style="float:center; width:36px; '
                                    + 'margin:0 5px 10px 0; max-height:64px;" src="data/images/news.png" />';
                        }
                        else if (type[0] == "Report") {
                            result += '<img style="float:center; width:36px; '
                                    + 'margin:0 5px 10px 0; max-height:64px;" src="data/images/report.png" />';
                        }
                        if (openurl)
                            result += '<span class="z3988" title="' + openurl + '" />'
                    }
                }
                result += '</div>';
                //result += '</td><td>';
            }
            else {
                //result += '<td>';
                id = options.data['ids'][index];
            }

            var family = id;
            // family id
            if (options['collection'] == 'patent') {
                if (family.length > 1) {
                    result += '<div class="span10" class="height:100%;" id="myCollapsible_' + index + '" data-toggle="collapse" data-target="#abstract_' + index + '" style="white-space:normal;">';
                    result += 'Family: ' + family;
                }
            }
            else {
                result += '<div class="span10" class="height:100%;" id="myCollapsible_' + index + '" data-toggle="collapse" data-target="#abstract_' + index + '" style="white-space:normal;">';
            }

            // date
            var date;
            var dates = null;
            if (options['collection'] == 'patent')
                dates = jsonObject['$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$monogr.$date'];
            else if (options.search_index == "summon") {
                dates = jsonObject['PublicationDate'];
            }
            else {
                dates = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$imprint.$date'];
                if (!dates) {
                    dates = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$imprint.$when'];
                }
                if (!dates) {
                    dates = jsonObject['$teiCorpus.$teiHeader.$editionStmt.$edition.$date'];
                }
            }

            if (options['collection'] == 'patent') {
                var rawDate = JSON.stringify(dates);
                var ind1 = rawDate.indexOf('"');
                var ind2 = rawDate.indexOf('"', ind1 + 1);
                date = rawDate.substring(ind1, ind2 + 1);

                if (date && (date.length > 1)) {
                    result += ' - <em>' + date.substring(7, date.length - 1) + '.' + date.substring(5, 7)
                            + '.' + date.substring(1, 5) + '</em>' + '<br />';
                }
            }

            var title;
            var titles = null;
            var titleID = null;
            var titleIDs = null;
            var titleAnnotated = null;
            if (options['collection'] == 'patent')
                titles = jsonObject['$teiCorpus.$teiHeader.$fileDesc.$titleStmt.$title.$lang_en'];
            else if ((options.search_index == "summon")) {
                titles = jsonObject['Title'];
            }
            else {
                // NPL
                titles = jsonObject['$teiCorpus.$teiHeader.$titleStmt.$title.$title-first'];
                titleIDs = jsonObject['$teiCorpus.$teiHeader.$titleStmt.xml:id'];
            }
            if (typeof titles == 'string') {
                title = titles;
            }
            else {
                if (titles) {
                    title = titles[0];
                    while ((typeof title != 'string') && (typeof title != 'undefined')) {
                        title = title[0];
                    }
                }
            }
            if (typeof titleIDs == 'string') {
                titleID = titleIDs;
            }
            else {
                if (titleIDs) {
                    titleID = titleIDs[0];
                    while ((typeof title != 'string') && (typeof title != 'undefined')) {
                        titleID = titleID[0];
                    }
                }
            }

            if (!title || (title.length == 0)) {
                if (options['collection'] == 'patent') {
                    titles = jsonObject['$teiCorpus.$teiHeader.$fileDesc.$titleStmt.$title.$lang_en'];
                }
                else {
                    titles = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$title.$lang_en'];
                }
                if (typeof titles == 'string') {
                    title = titles;
                }
                else {
                    if (titles) {
                        title = titles[0];
                        while ((typeof title != 'string') && (typeof title != 'undefined')) {
                            title = title[0];
                        }
                    }
                }
            }

            if (!title || (title.length == 0)) {
                if (options['collection'] == 'patent') {
                    titles = jsonObject['$teiCorpus.$teiHeader.$fileDesc.$titleStmt.$title.$lang_fr'];
                }
                else {
                    titles = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$title.$lang_fr'];
                }
                if (typeof titles == 'string') {
                    title = titles;
                }
                else {
                    if (titles) {
                        title = titles[0];
                        while ((typeof title != 'string') && (typeof title != 'undefined')) {
                            title = title[0];
                        }
                    }
                }
            }

            if (!title || (title.length == 0)) {
                if (options['collection'] == 'patent') {
                    titles = jsonObject['$teiCorpus.$teiHeader.$fileDesc.$titleStmt.$title.$lang_de'];
                }
                else {
                    titles = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$title.$lang_de'];
                }
                if (typeof titles == 'string') {
                    title = titles;
                }
                else {
                    if (titles) {
                        title = titles[0];
                        while ((typeof title != 'string') && (typeof title != 'undefined')) {
                            title = title[0];
                        }
                    }
                }
            }

            if (title && (title.length > 1) && !titleAnnotated) {
                if ((options['collection'] == 'npl') && (options.search_index != "summon")) {
                    var docid = id;
                    if (docid.indexOf('fulltext')) {
                        docid = docid.replace('.fulltext', '');
                    }
                    result += '<span style="color:grey">' + docid
                            + ' - </span> <strong><span '
                    if (titleID) {
                        result += ' id="titleNaked" pos="' + index + '" rel="' + titleID + '" ';
                    }
                    result += ' style="font-size:13px; color:black; white-space:normal;">' + title + '<span></strong>';
                }
                else {
                    result += '<strong><span style="font-size:13px">' + title + '<span></strong>';
                }
            }

            result += '<br />';

            if (options.search_index == "summon") {
                var authors = jsonObject['Author_xml'];
                if (!authors) {
                    // we can use alternatively corporate authors
                    authors = jsonObject['CorporateAuthor_xml'];
                    if (!authors) {
                        result += "Anonymous";
                    }
                    else {
                        if (authors.length > 0) {
                            var name = authors[0]['name'];
                            result += name + " ";
                        }
                    }
                }
                else if (authors.length < 4) {
                    var first = true;
                    for (var aut in authors) {
                        var fullname = authors[aut]['fullname'];
                        var firstname = authors[aut]['givenname'];
                        var middlename = authors[aut]['middlename'];
                        var lastname = authors[aut]['surname'];
                        if (!first) {
                            result += ", ";
                        }
                        else {
                            first = false;
                        }
                        if (firstname || lastname) {
                            if (firstname) {
                                if (firstname.length > 4)
                                    result += firstname + " ";
                                else
                                    result += firstname.replace(" ", ".") + '. ';
                            }
                            if (middlename) {
                                if (middlename.length > 4)
                                    result += middlename + " ";
                                else
                                    result += middlename.replace(" ", ".") + '. ';
                            }
                            if (lastname)
                                result += lastname;
                        }
                        else if (fullname != null) {
                            var ind = fullname.indexOf(",");
                            if (ind != -1) {
                                result += fullname.substring(ind + 1, fullname.length) + " " + fullname.substring(0, ind);
                            }
                            else
                                result += fullname;
                        }

                    }
                }
                else {
                    var fullname = authors[0]['fullname'];
                    var firstname = authors[0]['givenname'];
                    var lastname = authors[0]['surname'];
                    if (firstname || lastname) {
                        if (firstname)
                            result += firstname.replace(" ", ".") + '. ';
                        if (lastname)
                            result += lastname + ' et al.';
                    }
                    else {
                        result += fullname + ' et al.';
                    }
                }

            }
            else if (options['collection'] != 'patent') {
                var authorsLast = null;
                var authorsFirst = null;

                authorsLast = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$author.$persName.$surname'];
                var tempStr = "" + authorsLast;
                authorsLast = tempStr.split(",");
                authorsFirst = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$author.$persName.$forename'];
                tempStr = "" + authorsFirst;
                authorsFirst = tempStr.split(",");

                if (authorsLast.length < 4) {
                    for (var author in authorsLast) {
                        if (author == 0) {
                            if (authorsFirst.length > 0) {
                                result += authorsFirst[0][0] + ". ";
                            }
                            result += authorsLast[0];
                        }
                        else {
                            result += ", ";
                            if (authorsFirst.length > author) {
                                result += authorsFirst[author][0] + ". ";
                            }
                            result += authorsLast[author];
                        }
                    }
                }
                else {
                    if (authorsFirst.length > 0) {
                        result += authorsFirst[0][0] + ". ";
                    }
                    result += authorsLast[0] + ' et al.';
                }
            }

            // book, proceedings or journal title
            if (options.search_index == "summon") {
                var titlesBook = jsonObject['PublicationTitle'];
                if (!titlesBook) {
                    titlesBook = jsonObject['PublicationSeriesTitle'];
                }
                if (titlesBook) {
                    var titleBook = titlesBook[0]
                    if (titleBook && (titleBook.length > 1)) {
                        result += ' - <em>' + titleBook + '</em>';
                    }
                }
            }
            else if (options['collection'] == 'npl') {
                var titleBook = null;
                var titlesBook = null;
                //if (options['collection'] == 'npl') {
                titlesBook = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$title'];
                var titleBookTmp = null;
                if (typeof titlesBook == 'string') {
                    titleBook = titlesBook;
                }
                else {
                    titleBook = titlesBook;
                    while ((typeof titleBook != 'string') && (typeof titleBook != 'undefined')) {
                        titleBookTmp = titleBook[0];
                        if (typeof titleBookTmp != 'undefined') {
                            titleBook = titleBookTmp;
                        }
                        else {
                            for (var x in titleBook) {
                                titleBookTmp = titleBook[x];
                            }
                            if (titleBookTmp)
                                titleBook = titleBookTmp[0];
                            else
                                titleBook = null;
                            break;
                        }

                    }
                }
                //}
                if (titleBook && (titleBook.length > 1)) {
                    result += ' - <em>' + titleBook + '</em>';
                }
            }

            if (options.search_index == "summon") {
                var pageCount = jsonObject['PageCount'];

                if (pageCount) {
                    if (pageCount[0] == '0') {
                        result += ' - ' + '<em>less than 1 page. </em>'
                    }
                    else
                        result += ' - ' + '<em>' + pageCount[0] + ' pages. </em>'
                }

                var startPage = jsonObject['StartPage'];
                var endPage = jsonObject['EndPage'];

                var e1 = -1
                var e2 = -1;

                if (endPage) {
                    e2 = endPage[0];
                    if (startPage) {
                        e1 = startPage[0];

                        if ((e1 != -1) && (e2 != -1)) {
                            result += ' - ' + '<em>' + (e2 - e1 + 1) + ' pages. </em>'
                        }
                    }
                }

            }

            if (options['collection'] != 'patent') {
                if (options.search_index == "summon") {
                    if (!dates) {
                        result += ' -  <em> Published ';
                        result += 'date unknown';
                        result += '</em>' + '<br />';
                    }
                    else if (dates[0]) {
                        var year = dates[0]['year'];
                        var month = dates[0]['month'];
                        var day = dates[0]['day'];
                        result += ' -  <em> Published ';
                        if ((day != undefined) && (day.length > 0))
                            result += day + '.';
                        if ((month != undefined) && (month.length > 0))
                            result += month + '.';
                        if (year != undefined)
                            result += year + '</em>' + '<br />';
                    }
                }
                else {
                    var rawDate = JSON.stringify(dates);
                    if (rawDate != null) {
                        var ind1 = rawDate.indexOf('"');
                        var ind2 = rawDate.indexOf('"', ind1 + 1);
                        date = rawDate.substring(ind1, ind2 + 1);

                        if (date && (date.length > 1)) {
                            var year = date.substring(1, 5);
                            var month = null;
                            if (date.length > 6)
                                month = date.substring(6, 8);
                            if ((month) && (month.length > 1) && (month[0] == "0")) {
                                month = month.substring(1, month.length)
                            }
                            var day = null;
                            if (date.length > 9)
                                day = date.substring(9, date.length - 1);
                            if ((day != undefined) && (day.length > 1) && (day[0] == "0")) {
                                day = day.substring(1, day.length)
                            }
                            result += ' - <em>';
                            if ((day != undefined) && (day.length > 0))
                                result += day + '.';
                            if ((month != undefined) && (month.length > 0))
                                result += month + '.';
                            if (year != undefined)
                                result += year + '</em>' + '<br />';
                        }
                    }
                }
            }

            // snippets 
            // Dominique Andlauer's strategy (sort of Google's one), at least one snippet per matched term, then 
            // per relevance, first we check the number of different matched terms
            if (options.snippet_style == "andlauer") {
                if (highlight) {
                    var jsonObject2 = eval(highlight);
                    var newSnippets = [];
                    // we first list all term stems found in the full list of snippets
                    var activeTerms = [];
                    for (var n in jsonObject2) {
                        var snippets = jsonObject2[n];
                        for (var i = 0; i < snippets.length; i++) {
                            var indd = 0;
                            while (indd < snippets[i].length) {
                                var inddd = snippets[i].indexOf("<strong>", indd);
                                if (inddd != -1) {
                                    var inddd2 = snippets[i].indexOf("</strong>", inddd);
                                    if (inddd2 != -1) {
                                        var term = stemmer(snippets[i].substring(inddd + 8, inddd2).toLowerCase());
                                        if (activeTerms.length == 0) {
                                            activeTerms.push(term);
                                        }
                                        else {
                                            var present = false;
                                            for (var k in activeTerms) {
                                                if (activeTerms[k] == term) {
                                                    present = true;
                                                    break;
                                                }
                                            }
                                            if (!present) {
                                                activeTerms.push(term);
                                            }
                                        }
                                        indd = inddd2 + 1;
                                    }
                                    else {
                                        break;
                                    }
                                }
                                else {
                                    break;
                                    //indd = snippets[i].length;
                                }
                            }
                        }
                    }

                    // we then re-rank to have all the terms present in the highest ranked snippets
                    var passiveTerms = [];
                    var localTerms = [];
                    var remainingSnippets = [];
                    for (var n in jsonObject2) {
                        var snippets = jsonObject2[n];
                        for (var i = 0; i < snippets.length; i++) {
                            if (passiveTerms.length == activeTerms.length) {
                                remainingSnippets.push(snippets[i]);
                            }
                            var indd = 0;
                            while (indd < snippets[i].length) {
                                var inddd = snippets[i].indexOf("<strong>", indd);
                                if (inddd != -1) {
                                    var inddd2 = snippets[i].indexOf("</strong>", inddd);
                                    if (inddd2 != -1) {
                                        var term = stemmer(snippets[i].substring(inddd + 8, inddd2).toLowerCase());
                                        if (localTerms.length == 0) {
                                            localTerms.push(term);
                                        }
                                        else {
                                            var present = false;
                                            for (var k in activeTerms) {
                                                if (localTerms[k] == term) {
                                                    present = true;
                                                    break;
                                                }
                                            }
                                            if (!present)
                                                localTerms.push(term);
                                        }
                                        indd = inddd2 + 1;
                                    }
                                    else {
                                        break;
                                    }
                                }
                                else {
                                    break;
                                }
                            }
                            // shall we include snippets[i] as next snippet?
                            if (passiveTerms.length == 0) {
                                newSnippets.push(snippets[i]);
                                for (var dumm in localTerms) {
                                    passiveTerms.push(localTerms[dumm])
                                }
                            }
                            else {
                                var previousState = passiveTerms.length;
                                for (var dumm in localTerms) {
                                    var present = false;
                                    for (var k in passiveTerms) {
                                        if (passiveTerms[k] == localTerms[dumm]) {
                                            present = true;
                                            break;
                                        }
                                    }

                                    if (!present) {
                                        newSnippets.push(snippets[i]);
                                        for (var dumm2 in localTerms) {
                                            passiveTerms.push(localTerms[dumm2])
                                        }
                                    }
                                }
                                /*if (previousState == passiveTerms.length) {
                                 // no new term
                                 remainingSnippets.push(snippets[i]);
                                 }*/
                            }
                        }
                    }
                    // we complete the new snippet
                    for (var dumm in remainingSnippets) {
                        newSnippets.push(remainingSnippets[dumm]);
                    }

                    // we have the new snippets and can output them
                    var totalDisplayed = 0;
                    for (var dumm in newSnippets) {
                        if (newSnippets[dumm].length > 200) {
                            // we have an issue with the snippet boundaries
                            var indd = newSnippets[dumm].indexOf("<strong>");
                            var max = indd + 100;
                            if (max > newSnippets[dumm].length) {
                                max = newSnippets[dumm].length;
                            }
                            result += '...<span style="font-size:12px"><em>' + newSnippets[dumm].substring(indd, max) + '</em></span>...<br />';
                        }
                        else {
                            result += '...<span style="font-size:12px"><em>' + newSnippets[dumm] + '</em></span>...<br />';
                        }
                        totalDisplayed++;
                        if (totalDisplayed == 3) {
                            break;
                        }
                    }
                }
            }
            else {
                // here default strategy, snippet ranking per relevance
                if (options.search_index == "summon") {
                    if ($('#facetview_freetext').val()) {
                        if ($('#facetview_freetext').val().trim().length > 0) {
                            var snippets = jsonObject['Snippet'];
                            if (snippets) {
                                for (var snippet in snippets) {
                                    //var snip = snippets[snippet].replace(/h>/g,'strong>');
                                    var snip = snippets[snippet];
                                    result += '...<span style="font-size:12px"><em>' + snip + '</em></span>...<br />';
                                }
                            }
                        }
                    }
                }
                else if (highlight) {
                    var jsonObject2 = eval(highlight);
                    //var snippets = jsonObject2['_all'];
                    //console.log(snippets);
                    var totalDisplayed = 0;
                    for (var n in jsonObject2) {
                        var snippets = jsonObject2[n];
                        for (var i = 0; i < snippets.length; i++) {
                            if (snippets[i].length > 200) {
                                // we have an issue with the snippet boundaries
                                var indd = snippets[i].indexOf("<strong>");
                                var max = indd + 100;
                                if (max > snippets[i].length) {
                                    max = snippets[i].length;
                                }
                                result += '...<span style="font-size:12px"><em>' + snippets[i].substring(indd, max) + '</em></span>...<br />';
                            }
                            else {
                                result += '...<span style="font-size:12px"><em>' + snippets[i] + '</em></span>...<br />';
                            }
                            totalDisplayed++;
                            if (totalDisplayed == 3) {
                                break;
                            }
                        }
                        if (totalDisplayed == 3) {
                            break;
                        }
                    }
                }
            }

            result += '</div>';

            // add image where available
            if (options.display_images) {
                if ((options['collection'] == 'cendari')) {
                    // with summon we can use the image service
                    var img = jsonObject['thumbnail_s'];
                    var img2 = jsonObject['thumbnail_l'];
                    var img3 = jsonObject['thumbnail_m'];
                    var uri = jsonObject['URI'];
                    img[0] = 'data/cache/images/cendari_photo/' + family + ".png";
                    if (img && img2) {
                        result += '<div class="span2"><img alt="bla" src="' + img[0] + '" pbsrc="' + img2[0] +
                                '" class="PopBoxImageSmall" pbRevertText="" onclick="Pop(this,50,\'PopBoxImageLarge\');" /></div>';
                    }
                    else if (img && img3) {
                        result += '<div class="span2"><img alt="bla" src="' + img[0] + '" pbsrc="' + img3[0] +
                                '" class="PopBoxImageSmall" pbRevertText="" onclick="Pop(this,50,\'PopBoxImageLarge\');" /></div>';
                    }
                    else if (img) {
                        if (uri) {
                            result += '<div class="span2"><a href="' + uri[0] + '" target="_blank""><img class="thumbnail" style="float:right; max-width:100px; '
                                    + 'max-height:150px;" src="' + img[0] + '" /></a></div>';
                        }
                        else {
                            result += '<div class="span2"><img class="thumbnail" style="float:right; max-width:100px; '
                                    + 'max-height:150px;" src="' + img[0] + '" /></div>';
                        }
                    }
                    else {
                        result += '<div class="span2" />';
                    }
                }
                else if (options.search_index == "summon") {
                    // with summon we can use the image service
                    var img = jsonObject['thumbnail_s'];
                    var img2 = jsonObject['thumbnail_l'];
                    var img3 = jsonObject['thumbnail_m'];
                    if (img && img2) {
                        result += '<td><img alt="bla" src="' + img[0] + '" pbsrc="' + img2[0] +
                                '" class="PopBoxImageSmall" pbRevertText="" onclick="Pop(this,50,\'PopBoxImageLarge\');" /></td>';
                    }
                    else if (img && img3) {
                        result += '<td><img alt="bla" src="' + img[0] + '" pbsrc="' + img3[0] +
                                '" class="PopBoxImageSmall" pbRevertText="" onclick="Pop(this,50,\'PopBoxImageLarge\');" /></td>';
                    }
                    else if (img) {
                        result += '<td><img class="thumbnail" style="float:right; max-width:100px; '
                                + 'max-height:150px;" src="' + img[0] + '" /></td>';
                    }
                    else {
                        result += '<td></td>';
                    }
                }
                else {
                    var ind = family.indexOf("-");
                    if ((ind != -1) && (family.length > ind)) {
                        var pubNum = family.substring(ind + 1, family.length);
                        result += '<div class="span2"><a href="https://hal.archives-ouvertes.fr/' + family +
                                '/document" target="_blank"><img class="thumbnail" style="float:right; " src="' +
                                'https://hal.archives-ouvertes.fr/' + family + '/thumb' + '" /></a></div>';
                    }
                    else {
                        result += '<div class="span2" />';
                    }
                }
            }

            //result += '</tr></table>';
            result += '</div>';

            result += '<div class="row-fluid"><div id="abstract_' + index +
                    '" class="collapse">';  //#f8f8f8
            if (index % 2) {
                result += '<div class="mini-layout fluid" style="background-color:#f8f8f8; padding-right:0px;">';
            }
            else {
                result += '<div class="mini-layout fluid" style="background-color:#ffffff;">';
            }
            //result += '<div class="class="container-fluid" style="border: 1px solid #DDD;">';
            result += '<div class="row-fluid">';

            if (options.search_index == "summon") {
                /*if (index % 2) {
                 result += '<div class="mini-layout fluid" style="background-color:#f8f8f8;">';
                 }
                 else {
                 result += '<div class="mini-layout fluid" style="background-color:#ffffff;">';
                 }
                 //result += '<div class="class="container-fluid" style="border: 1px solid #DDD;">';
                 result += '<div class="row-fluid">';
                 result += '<div class="span3">';*/
                result += '<div class="span3">';
                // extra biblo, if any
                if (type) {
                    if (type.length > 0) {
                        result += '<p style="height:12px;"><strong>Type: </strong> ';
                        var first = true;
                        for (var typ in type) {
                            var ty = type[typ];
                            if (first) {
                                result += ty;
                                first = false;
                            }
                            else {
                                result += ', ' + ty;
                            }
                        }
                        result += '</p>';
                    }
                }
                var isbn = jsonObject['ISBN'];
                if (isbn) {
                    var isbnn = isbn[0]
                    if (isbnn) {
                        result += '<p style="height:12px;"><strong>ISBN: </strong> ' + isbnn + '</p>';
                    }
                }
                var issn = jsonObject['ISSN'];
                if (issn) {
                    var issnn = issn[0]
                    if (issnn) {
                        result += '<p style="height:12px;"><strong>ISSN: </strong> ' + issnn + '</p>';
                    }
                }
                var vol = jsonObject['Volume'];
                if (vol) {
                    var volu = vol[0]
                    if (volu) {
                        result += '<p style="height:12px;"><strong>Volume: </strong> ' + volu + '</p>';
                    }
                }
                var issue = jsonObject['Issue'];
                if (issue) {
                    var issuee = issue[0]
                    if (issuee) {
                        result += '<p style="height:12px;"><strong>Issue: </strong> ' + issuee + '</p>';
                    }
                }

                result += '</div>';
                result += '<div class="span9">'; //#FDF5E1
                // abstract, if any
                var abstract_ = jsonObject['Abstract'];
                if (abstract_) {
                    var abstractt = abstract_[0]
                    if (abstractt) {
                        result += ' <strong>Abstract: </strong> ' + abstractt + '';
                    }
                }

                result += '</div>';
                result += '</div>';
            }
            else {
                // we need to retrieve the extra biblio and abstract for this biblo item
                result +=
                        '<div class="row-fluid" id="innen_abstract" pos="' + index + '" rel="' + family + '">';
                //'"><div style="background:url(data/images/bar-loader.gif) '+
                //'no-repeat center center; height:13px; "/></div>';
                result += '</div>';
                result += '</div>';
            }

            //result += '</div>';
            result += '</div>';
            result += "</div>";

            result += "</div>";

            if ((options['collection'] == 'npl') && (options.search_index != "summon")
                    && (options['collection'] != 'cendari')) {
                var pdfURL = jsonObject['$teiCorpus.$teiHeader.$sourceDesc.target'];
                var docid = jsonObject._id;
                if (pdfURL || docid) {
                    result += '<td>';
                    if (pdfURL) {
                        result += '<a href="' + pdfURL
                                + '" target="_blank"><img src="data/images/pdf_icon_small.gif"></a>';
                    }
                    if (docid) {
                        if (options['subcollection'] == 'hal') {
                            result += '<a href="http://hal.archives-ouvertes.fr/' + docid + '/en" target="_blank">hal</a>';
                        }
                        else if ((options['subcollection'] == 'zfn') && pdfURL) {
                            //var urlToHeader = pdfURL.replace("pdf", "header.tei.xml");
                            pdfURL = '' + pdfURL;
                            var urlToHeader = pdfURL.replace(/pdf/g, 'header.tei.xml');
                            result += '<a href="' + urlToHeader + '" target="_blank">tei</a>';
                        }
                    }
                    result += '</td>';
                }
            }
            else {
                result += '<td></td>';
            }

            result += '</td></tr>';

            node.append(result);
        }

        // view a full record when selected - not used!
        var viewrecord = function (event) {
            event.preventDefault();
            var record = options.data['records'][$(this).attr('href')]
            alert(JSON.stringify(record, "", "    "))
        }

        // put the results on the page
        showresults = function (sdata) {
            // get the data and parse from elasticsearch or other 
            var data = null;
            if (options.search_index == "summon") {
                data = parseresultsSummons(sdata);
            }
            else if (options.search_index == "elasticsearch") {
                // default is elasticsearch
                data = parseresultsElasticSearch(sdata);
            }
            else {
                // nothing to do :(
                return;
            }
            options.data = data;

            // put result metadata on the page
            putmetadata(data);
            // put the filtered results on the page
            $('#facetview_results').html("");
            //var infofiltervals = new Array();
            $.each(data.records, function (index, value) {
                // write them out to the results div
                //$('#facetview_results').append( buildrecord(index) );
                buildrecord(index, $('#facetview_results'));
                $('#facetview_results tr:last-child').linkify();
            });
            MathJax.Hub.Queue(["Typeset", MathJax.Hub]);
            // change filter options
            putvalsinfilters(data);

            // for the first time we visualise the filters as defined in the facet options
            for (var each in options.facets) {
                if (options.facets[each]['view'] == 'graphic') {
                    //if ($('.facetview_filterselected[rel=' + options.facets[each]['field'] + "]" ).length == 0 )
                    if ($('#facetview_visualisation_' + options.facets[each]['display'] + '_chart').length == 0)
                        $('.facetview_visualise[href=' + options.facets[each]['display'] + ']').trigger('click');
                }
                else if ((!$('.facetview_filtershow[rel=' + options.facets[each]['display'] +
                        ']').hasClass('facetview_open'))
                        && (options.facets[each]['view'] == 'textual')) {
                    $('.facetview_filtershow[rel=' + options.facets[each]['display'] + ']').trigger('click');
                }
            }

            //we load now in background the additional record information requiring a user interaction for
            // visualisation - this is not require for summon
            if (options.search_index != "summon") {
                $('#titleNaked', obj).each(function () {
                    if (options.collection == "npl") {
                        // annotations for the title
                        var index = $(this).attr('pos');
                        var titleID = $(this).attr('rel');
                        var localQuery = {"query": {"filtered": {"query": {"term": {"_id": titleID}}}}};

                        $.ajax({
                            type: "get",
                            url: options.search_url_annotations,
                            contentType: 'application/json',
                            dataType: 'jsonp',
                            data: {source: JSON.stringify(localQuery)},
                            success: function (data) {
                                displayAnnotations(data, index, titleID, 'title');
                            }
                        });
                    }
                });

                $('#innen_abstract', obj).each(function () {
                    // load biblio and abstract info. 
                    // pos attribute gives the result index, rel attribute gives the document ID 
                    var index = $(this).attr('pos');
                    var docID = $(this).attr('rel');
                    var localQuery;

                    if (options.collection == "npl") {

                        // abstract and further informations
                        localQuery = {"fields": ["$teiCorpus.$teiHeader.$profileDesc.xml:id",
                                "$teiCorpus.$teiHeader.$profileDesc.$abstract.$lang_en",
                                "$teiCorpus.$teiHeader.$profileDesc.$abstract.$lang_fr",
                                "$teiCorpus.$teiHeader.$profileDesc.$abstract.$lang_de",
                                "$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$title.$title-first",
                                "$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$idno.$type_doi",
                                "$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$author.$persName.$fullName",
                                '$teiCorpus.$teiHeader.$profileDesc.$textClass.$classCode.$scheme_halTypology',
                                "$teiCorpus.$teiHeader.$profileDesc.$textClass.$keywords.$type_author.$term",
                                '$teiCorpus.$teiHeader.$profileDesc.$textClass.$keywords.$type_author.xml:id'],
                            "query": {"filtered": {"query": {"term": {"_id": docID}}}}};

                        $.ajax({
                            type: "get",
                            url: options.search_url,
                            contentType: 'application/json',
                            dataType: 'jsonp',
                            data: {source: JSON.stringify(localQuery)},
                            success: function (data) {
                                displayAbstract(data, index);
                            }
                        });

                    }
                    else if (options.collection == "patent") {
                        localQuery = {"fields": ["_id",
                                "$teiCorpus.$TEI.$text.$front.$div.$p.$lang_en",
                                "$teiCorpus.$TEI.$text.$front.$div.$p.$lang_de",
                                "$teiCorpus.$TEI.$text.$front.$div.$p.$lang_fr"],
                            "query": {"filtered": {"query": {"term": {"_id": docID}}}}};

                        /*$.post(options.search_url, 
                         {source : JSON.stringify(localQuery) }, 
                         function(data) { displayAbstract(data, index); }, 
                         "jsonp");*/
                        $.ajax({
                            type: "get",
                            url: options.search_url,
                            contentType: 'application/json',
                            dataType: 'jsonp',
                            data: {source: JSON.stringify(localQuery)},
                            success: function (data) {
                                displayAbstract(data, index);
                            }
                        });
                    }
                });
            }
        }

        /**
         *  This is a primitive function that return the json object corresponding to a 
         *  loosy elasticsearch json path in the array type of structures return by elasticsearch
         *  fields query parameter.
         * 
         *  In case of multiple sub array (corresponding to several distinct results following
         *  the same elasticsearch json path), we aggregate the final field values in the result.  
         * 
         *  The result is an array. 
         */
        var accessJsonPath = function (jsonArray, path) {
            var res = [];
            if (!path) {
                return res;
            }

            var subPath = null;
            var indd = path.indexOf('.');
            if (indd != -1) {
                subPath = path.substring(indd + 1, path.length);
            }

            if (!subPath) {
                for (var subObj in jsonArray) {
                    res.push(jsonArray[subObj]);
                }
            }
            else {
                for (var subObj in jsonArray) {
                    var localRes = accessJsonPath(jsonArray[subObj], subPath)
                    for (var ress in localRes) {
                        res.push(localRes[ress]);
                    }
                }
            }

            return res;
        }

        var displayAnnotations = function (data, index, id, origin) {
            var jsonObject = null;
            if (!data) {
                return;
            }
            if (data.hits) {
                if (data.hits.hits) {
                    jsonObject = eval(data.hits.hits[0]);
                }
            }
            if (!jsonObject) {
                return;
            }

            // origin is title, abstract or keywords
            if (!options.data['' + origin]) {
                options.data['' + origin] = [];
            }
            if (origin == 'keyword') {
                if (!options.data['' + origin][index]) {
                    options.data['' + origin][index] = [];
                }
                options.data['' + origin][index][id] = jsonObject['_source']['annotation']['nerd'];
            }
            else
                options.data['' + origin][index] = jsonObject['_source']['annotation']['nerd'];
            //console.log('annotation for ' + id);
            //console.log(jsonObject);

            //var text = jsonObject['_source']['annotation']['nerd']['text'];		
            var text = $('[rel="' + id + '"]').text();
            var entities = jsonObject['_source']['annotation']['nerd']['entities'];
            var m = 0;
            var lastMaxIndex = text.length;
            //for(var m in entities) {
            for (var m = entities.length - 1; m >= 0; m--) {
                //var entity = entities[entities.length - m - 1];
                var entity = entities[m];
                var chunk = entity.rawName;
                var domains = entity.domains;
                var domain = null;
                if (domains && domains.length > 0) {
                    domain = domains[0].toLowerCase();
                }
                var label = null;
                if (entity.type)
                    label = NERTypeMapping(entity.type, entity.chunk);
                else if (domain)
                    label = domain;
                else
                    label = chunk;
                var start = parseInt(entity.offsetStart, 10);
                var end = parseInt(entity.offsetEnd, 10);

                // keeping track of the lastMaxIndex allows to handle nbest results, e.g. possible
                // overlapping annotations to display as infobox, but with only one annotation
                // tagging the text
                if (start > lastMaxIndex) {
                    // we have a problem in the initial sort of the entities
                    // the server response is not compatible with the client 
                    console.log("Sorting of entities as present in the server's response not valid for this client.");
                }
                else if ((start == lastMaxIndex) || (end > lastMaxIndex)) {
                    // overlap
                    end = lastMaxIndex;
                }
                else {
                    // we produce the annotation on the string
                    if (origin == "abstract") {
                        text = text.substring(0, start) +
                                '<span id="annot-abs-' + index + '-' + (entities.length - m - 1) +
                                '" rel="popover" data-color="' + label + '">' +
                                '<span class="label ' + label +
                                '" style="cursor:hand;cursor:pointer;white-space: normal;" >'
                                + text.substring(start, end) + '</span></span>'
                                + text.substring(end, text.length + 1);
                    }
                    else if (origin == "keyword") {
                        text = text.substring(0, start) +
                                '<span id="annot-key-' + index + '-' + (entities.length - m - 1) + '-' + id
                                + '" rel="popover" data-color="' + label + '">' +
                                '<span class="label ' + label + '" style="cursor:hand;cursor:pointer;" >'
                                + text.substring(start, end) + '</span></span>'
                                + text.substring(end, text.length + 1);
                    }
                    else {
                        text = text.substring(0, start) +
                                '<span id="annot-' + index + '-' + (entities.length - m - 1) +
                                '" rel="popover" data-color="' + label + '">' +
                                '<span class="label ' + label + '" style="cursor:hand;cursor:pointer;" >'
                                + text.substring(start, end) + '</span></span>'
                                + text.substring(end, text.length + 1);
                    }
                    lastMaxIndex = start;
                }
            }

            //var result = '<strong><span style="font-size:13px">' + text + '<span></strong>';
            if (origin == "abstract")
                $('[rel="' + id + '"]').html('<strong>Abstract: </strong>' + text);
            else
                $('[rel="' + id + '"]').html(text);

            // now set the popovers/view event 
            var m = 0;
            for (var m in entities) {
                // set the info box
                if (origin == "abstract")
                    $('#annot-abs-' + index + '-' + m).bind('hover', viewEntity);
                else if (origin == "keyword")
                    $('#annot-key-' + index + '-' + m + '-' + id).bind('hover', viewEntity);
                else
                    $('#annot-' + index + '-' + m).bind('hover', viewEntity);
            }
        }

        var displayAbstract = function (data, index) {
            var jsonObject = null;
            if (!data) {
                return;
            }
            if (data.hits) {
                if (data.hits.hits) {
                    jsonObject = eval(data.hits.hits[0]);
                }
            }
            if (!jsonObject) {
                return;
            }

            if (options.collection == "npl") {
                var docid = jsonObject._id;
                var piece = "";

                //piece += '<div class="row-fluid">';

                piece += '<div class="span2" style="width:13%;">';
                if (options.subcollection == "hal") {
                    piece += '<p><strong> <a href="https://hal.archives-ouvertes.fr/'
                            + docid + '" target="_blank" style="text-decoration:underline;">'
                            + docid + '</a></strong></p>';


                    // document type
                    var type =
                            jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.$textClass.$classCode.$scheme_halTypology'];
                    if (type) {
                        piece += '<p><span class="label pubtype" style="white-space:normal;">' + type + '</span></p>';
                        //piece += '<p><strong>' + type + '</strong></p>';
                    }
                }

                // authors and affiliation
                var names =
                        jsonObject.fields['$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$author.$persName.$fullName'];

                if (names) {
                    for (var aut in names) {
                        var name_ = names[aut];
                        piece += '<p>' + name_ + '</p>';
                    }
                }

                piece += '</div>';

                piece += '<div class="span6" style="margin-left:10px;">';
                // abstract, if any
                var abstract = null;

                var abstractID = null;
                var abstractIDs = jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.xml:id'];
                if (typeof abstractIDs == 'string') {
                    abstractID = abstractIDs;
                }
                else {
                    if (abstractIDs && (abstractIDs.length > 0)) {
                        abstractID = abstractIDs[0];
                        while ((typeof abstractID != 'string') && (typeof abstractID != 'undefined')) {
                            abstractID = abstractID[0];
                        }
                    }
                }

                var abstracts = jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.$abstract.$lang_en'];
                if (typeof abstracts == 'string') {
                    abstract = abstracts;
                }
                else {
                    if (abstracts && (abstracts.length > 0)) {
                        abstract = abstracts[0];
                        while ((typeof abstract != 'string') && (typeof abstract != 'undefined')) {
                            abstract = abstract[0];
                        }
                    }
                }

                if (!abstract || (abstract.length == 0)) {
                    abstracts = jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.$abstract.$lang_fr'];

                    if (typeof abstracts == 'string') {
                        abstract = abstracts;
                    }
                    else {
                        if (abstracts && (abstracts.length > 0)) {
                            abstract = abstracts[0];
                            while ((typeof abstract != 'string') && (typeof abstract != 'undefined')) {
                                abstract = abstract[0];
                            }
                        }
                    }
                }

                if (!abstract || (abstract.length == 0)) {
                    abstracts = jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.$abstract.$lang_de'];

                    if (typeof abstracts == 'string') {
                        abstract = abstracts;
                    }
                    else {
                        if (abstracts && (abstracts.length > 0)) {
                            abstract = abstracts[0];
                            while ((typeof abstract != 'string') && (typeof abstract != 'undefined')) {
                                abstract = abstract[0];
                            }
                        }
                    }
                }

                if (!abstract || (abstract.length == 0)) {
                    abstracts = jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.$abstract.$lang_es'];

                    if (typeof abstracts == 'string') {
                        abstract = abstracts;
                    }
                    else {
                        if (abstracts && (abstracts.length > 0)) {
                            abstract = abstracts[0];
                            while ((typeof abstract != 'string') && (typeof abstract != 'undefined')) {
                                abstract = abstract[0];
                            }
                        }
                    }
                }

                if (abstract && (abstract.length > 0) && (abstract.trim().indexOf(" ") != -1)) {
                    piece += '<p id="abstractNaked" pos="' + index + '" rel="' + abstractID + '" >' + abstract + '</p>';
                }

                // keywords
                var keyword = null;
                var keywordIDs =
                        jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.$textClass.$keywords.$type_author.xml:id'];
                // we have a list of keyword IDs, each one corresponding to an independent annotation set
                var keywords =
                        jsonObject.fields['$teiCorpus.$teiHeader.$profileDesc.$textClass.$keywords.$type_author.$term'];

                if (typeof keywords == 'string') {
                    keyword = keywords;
                }
                else {
                    var keyArray = keywords;
                    if (keyArray) {
                        for (var p in keyArray) {
                            var keywordID = keywordIDs[p];
                            if (p == 0) {
                                keyword = '<span id="keywordsNaked"  pos="' + index + '" rel="' + keywordID + '">'
                                        + keyArray[p] + '</span>';
                            }
                            else {
                                keyword += ', ' + '<span id="keywordsNaked"  pos="' + index + '" rel="' + keywordID + '">' +
                                        keyArray[p] + '</span>';
                            }
                        }
                    }
                }

                if (keyword && (keyword.length > 0) && (keyword.trim().indexOf(" ") != -1)) {
                    piece += ' <p><strong>Keywords: </strong> ' + keyword + '</p>';
                }

                piece += '</div>';

                // info box for the entities
                piece += '<div class="span4" style="margin-left:10px; width:35%;">';
                piece += '<span id="detailed_annot-' + index + '" />';
                piece += "</div>";

                piece += "</div>";

                $('#innen_abstract[rel="' + docid + '"]').append(piece);

                $('#abstractNaked[rel="' + abstractID + '"]', obj).each(function () {
                    // annotations for the abstract
                    var index = $(this).attr('pos');
                    var titleID = $(this).attr('rel');
                    var localQuery = {"query": {"filtered": {"query": {"term": {"_id": abstractID}}}}};

                    $.ajax({
                        type: "get",
                        url: options.search_url_annotations,
                        contentType: 'application/json',
                        dataType: 'jsonp',
                        data: {source: JSON.stringify(localQuery)},
                        success: function (data) {
                            displayAnnotations(data, index, abstractID, 'abstract');
                        }
                    });
                });

                for (var p in keywordIDs) {
                    $('#keywordsNaked[rel="' + keywordIDs[p] + '"]', obj).each(function () {
                        // annotations for the keywords
                        var index = $(this).attr('pos');
                        var keywordID = $(this).attr('rel');
                        var localQuery = {"query": {"filtered": {"query": {"term": {"_id": keywordID}}}}};

                        $.ajax({
                            type: "get",
                            url: options.search_url_annotations,
                            contentType: 'application/json',
                            dataType: 'jsonp',
                            data: {source: JSON.stringify(localQuery)},
                            success: function (data) {
                                displayAnnotations(data, index, keywordID, 'keyword');
                            }
                        });
                    });
                }
            }
            else if (options.collection == "patent") {
                var docid = jsonObject._id;
                var piece = "";

                var piece = "";

                piece += '<div class="row-fluid">';

                piece += '<div class="span3">';
                piece += "<strong>Family ID:</strong> " + docid;

                piece += '</div>';

                piece += '<div class="span9">';
                // abstract, if any
                var abstract = null;
                var abstracts = jsonObject.fields['$teiCorpus.$teiCorpus.$TEI.$text.$front.$div.$p.$lang_en'];
                if (typeof abstracts == 'string') {
                    abstract = abstracts;
                }
                else {
                    if (abstracts && (abstracts.length > 0)) {
                        abstract = abstracts[0];
                        while ((typeof abstract != 'string') && (typeof abstract != 'undefined')) {
                            abstract = abstract[0];
                        }
                    }
                }

                if (!abstract || (abstract.length == 0)) {
                    abstracts = data['$teiCorpus.$teiCorpus.$TEI.$text.$front.$div.$p.$lang_de'];

                    if (typeof abstracts == 'string') {
                        abstract = abstracts;
                    }
                    else {
                        if (abstracts && (abstracts.length > 0)) {
                            abstract = abstracts[0];
                            while ((typeof abstract != 'string') && (typeof abstract != 'undefined')) {
                                abstract = abstract[0];
                            }
                        }
                    }
                }

                if (!abstract || (abstract.length == 0)) {
                    abstracts = data['$teiCorpus.$teiCorpus.$TEI.$text.$front.$div.$p.$lang_fr'];

                    if (typeof abstracts == 'string') {
                        abstract = abstracts;
                    }
                    else {
                        if (abstracts && (abstracts.length > 0)) {
                            abstract = abstracts[0];
                            while ((typeof abstract != 'string') && (typeof abstract != 'undefined')) {
                                abstract = abstract[0];
                            }
                        }
                    }
                }

                if (abstract && (abstract.length > 0)) {
                    piece += ' <strong>Abstract: </strong> ' + abstract + '';
                }

                piece += '</div>';

                piece += "</div>";

                $('#innen_abstract[rel="' + docid + '"]').append(piece);
            }
        }

        /** 
         * View the full entity information in the infobox 
         */
        function viewEntity(event) {
            event.preventDefault();
            // currently entity can appear in the title, abstract or keywords
            // the origin is visible in the event origin id, as well as the "coordinates" of the entity 

            var localID = $(this).attr('id');
            //console.log(localID);

            var resultIndex = -1;
            var abstractSentenceNumber = -1;
            var entityNumber = -1;
            var idNumber = null;

            var inAbstract = false;
            var inKeyword = false;
            if (localID.indexOf("-abs-") != -1) {
                // the entity is located in the abstract
                inAbstract = true;
                var ind1 = localID.indexOf('-');
                ind1 = localID.indexOf('-', ind1 + 1);
                //var ind2 = localID.indexOf('-', ind1+1);
                var ind3 = localID.lastIndexOf('-');
                resultIndex = parseInt(localID.substring(ind1 + 1, ind3));
                //abstractSentenceNumber = parseInt(localID.substring(ind2+1,ind3));
                entityNumber = parseInt(localID.substring(ind3 + 1, localID.length));
            }
            else if (localID.indexOf("-key-") != -1) {
                // the entity is located in the keywords
                inKeyword = true;
                var ind1 = localID.indexOf('-');
                ind1 = localID.indexOf('-', ind1 + 1);
                var ind2 = localID.indexOf('-', ind1 + 1);
                var ind3 = localID.lastIndexOf('-');
                resultIndex = parseInt(localID.substring(ind1 + 1, ind3));
                entityNumber = parseInt(localID.substring(ind2 + 1, ind3));
                idNumber = localID.substring(ind3 + 1, localID.length);
            }
            else {
                // the entity is located in the title
                var ind1 = localID.indexOf('-');
                var ind2 = localID.lastIndexOf('-');
                resultIndex = parseInt(localID.substring(ind1 + 1, ind2));
                entityNumber = parseInt(localID.substring(ind2 + 1, localID.length));

                // and, if not expended, we need to expend the record collapsable to show the info box
                //('#myCollapsible_'+resultIndex).collapse('show');
            }

            var entity = null;
            var localSize = -1;

            if (inAbstract) {
                //console.log(resultIndex + " " + entityNumber);
                //console.log(options.data['abstract'][resultIndex]['entities']);

                if ((options.data['abstract'][resultIndex])
                        && (options.data['abstract'][resultIndex])
                        && (options.data['abstract'][resultIndex]['entities'])
                        ) {
                    localSize = options.data['abstract'][resultIndex]
                            ['entities'].length;
                    entity = options.data['abstract'][resultIndex]
                            ['entities'][localSize - entityNumber - 1];
                }
            }
            else if (inKeyword) {
                //console.log(resultIndex + " " + entityNumber + " " + idNumber);
                //console.log(options.data['keyword'][resultIndex][idNumber]['entities']);

                if ((options.data['keyword'][resultIndex])
                        && (options.data['keyword'][resultIndex][idNumber])
                        && (options.data['keyword'][resultIndex][idNumber]['entities'])
                        ) {
                    localSize = options.data['keyword'][resultIndex][idNumber]
                            ['entities'].length;
                    entity = options.data['keyword'][resultIndex][idNumber]
                            ['entities'][localSize - entityNumber - 1];
                }
            }
            else {
                //console.log(resultIndex + " " + " " + entityNumber);
                //console.log(options.data['title'][resultIndex]['entities']);

                if ((options.data['title'])
                        && (options.data['title'][resultIndex])
                        && (options.data['title'][resultIndex]['entities'])
                        ) {
                    localSize = options.data['title'][resultIndex]['entities'].length;
                    entity = options.data['title'][resultIndex]['entities'][localSize - entityNumber - 1];
                }
            }

            var string = "";
            if (entity != null) {
                //console.log(entity);
                var domains = entity.domains;
                if (domains && domains.length > 0) {
                    domain = domains[0].toLowerCase();
                }
                var type = entity.type;

                var colorLabel = null;
                if (type)
                    colorLabel = type;
                else if (domains && domains.length > 0) {
                    colorLabel = domain;
                }
                else
                    colorLabel = entity.rawName;

                var start = parseInt(entity.offsetStart, 10);
                var end = parseInt(entity.offsetEnd, 10);

                var subType = entity.subtype;
                var conf = entity.nerd_score;
                if (conf && conf.length > 4)
                    conf = conf.substring(0, 4);
                var definitions = entity.definitions;
                var wikipedia = entity.wikipediaExternalRef;
                var freebase = entity.freeBaseExternalRef;
                var content = entity.rawName; //$(this).text();
                var preferredTerm = entity.preferredTerm;

                var sense = null;
                if (entity.sense)
                    sense = entity.sense.fineSense;

                string += "<div class='info-sense-box " + colorLabel +
                        "'><h3 style='color:#FFF;padding-left:10px;'>" + content.toUpperCase() +
                        "</h3>";
                string += "<div class='container-fluid' style='background-color:#F9F9F9;color:#70695C;border:padding:5px;margin-top:5px;'>" +
                        "<table style='width:100%;background-color:#fff;border:0px'><tr style='background-color:#fff;border:0px;'><td style='background-color:#fff;border:0px;'>";

                if (type)
                    string += "<p>Type: <b>" + type + "</b></p>";

                if (sense)
                    string += "<p>Sense: <b>" + sense + "</b></p>";

                if (domains && domains.length > 0) {
                    string += "<p>Domains: <b>";
                    for (var i = 0; i < domains.length; i++) {
                        if (i != 0)
                            string += ", ";
                        string += domains[i].replace("_", " ");
                    }
                    string += "</b></p>";
                }

                if (preferredTerm) {
                    string += "<p>Preferred: <b>" + preferredTerm + "</b></p>";
                }

                string += "<p>conf: <i>" + conf + "</i></p>";

                string += "</td><td style='align:right;background-color:#fff'>";

                if (freebase != null) {
                    var urlImage = 'https://usercontent.googleapis.com/freebase/v1/image' + freebase;
                    urlImage += '?maxwidth=150';
                    urlImage += '&maxheight=150';
                    urlImage += '&key=' + api_key;
                    string += '<img src="' + urlImage + '" alt="' + freebase + '"/>';
                }

                string += "</td></tr></table>";

                if ((definitions != null) && (definitions.length > 0)) {
                    string += "<p>" + definitions[0].definition + "</p>";
                }
                if ((wikipedia != null) || (freebase != null)) {
                    string += '<p>Reference: '
                    if (wikipedia != null) {
                        string += '<a href="http://en.wikipedia.org/wiki?curid=' +
                                wikipedia +
                                '" target="_blank"><img style="max-width:28px;max-height:22px;margin-top:5px;" src="data/images/wikipedia.png"/></a>';
                    }
                    if (freebase != null) {
                        string += '<a href="http://www.freebase.com' +
                                freebase +
                                '" target="_blank"><img style="max-width:28px;max-height:22px;margin-top:5px;" src="data/images/freebase_icon.png"/></a>';

                    }
                    string += '</p>';
                }

                string += "</div></div>";

                $('#detailed_annot-' + resultIndex).html(string);
                $('#detailed_annot-' + resultIndex).show();
            }
        }

        var textFieldPatents = ['$teiCorpus.$TEI.$teiHeader.$fileDesc.$titleStmt.$title.',
            '$teiCorpus.$TEI.$text.$front.$div.$p.',
            '$teiCorpus.$TEI.$text.$body.$div.$div.',
            '$teiCorpus.$TEI.$text.$body.$div.$p.'
        ];

        var textFieldNPL = ['$teiCorpus.$teiHeader.$titleStmt.$title.$title-first',
            '$teiCorpus.$text.$front.$div.$p.',
            '$teiCorpus.$text.$body.$head.',
            '$teiCorpus.$text.$body.$div.',
            '$teiCorpus.$text.$body.$figure.$head.',
            '$teiCorpus.$text.$body.$p.'
        ];

        var textFieldsPatentReturned = ['$teiCorpus.$TEI.$teiHeader.$fileDesc.$titleStmt.$title.$lang_de',
            '$teiCorpus.$TEI.$teiHeader.$fileDesc.$titleStmt.$title.$lang_en',
            '$teiCorpus.$TEI.$teiHeader.$fileDesc.$titleStmt.$title.$lang_fr',
            '$teiCorpus.$TEI.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$monogr.$date',
            '_id'
        ];

        var textFieldsNPLReturned = ['$teiCorpus.$teiHeader.$titleStmt.$title.$title-first',
            '$teiCorpus.$teiHeader.$titleStmt.xml:id',
            '$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$imprint.$date',
            '$teiCorpus.$teiHeader.$editionStmt.$edition.$date',
            '$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$author.$persName.$surname',
            '$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$analytic.$author.$persName.$forename',
            '$teiCorpus.$teiHeader.$sourceDesc.target',
//			'$teiCorpus.$teiHeader.$profileDesc.$textClass.$classCode.$scheme_halTypology',
            '_id'
        ];

        var computeIdString = function (acceptType, date, host, path, queryParameters) {
            return appendStrings(acceptType, date, host, path, computeSortedQueryString(queryParameters));
        }

        var computeSortedQueryString = function (queryParameters) {
            // parameters are stored in a json object
            // we sort
            queryParameters = queryParameters.sort(function (a, b) {
                for (var key1 in a) {
                    for (var key2 in b) {
                        if (key1 < key2)
                            return -1;
                        else if (key1 > key2)
                            return 1;
                        else {
                            return a[key1] < b[key2] ? -1 : 1;
                        }
                    }
                }
            });
            var parameterStrings = "";
            var first = true;
            for (var param in queryParameters) {
                var obj = queryParameters[param];
                for (var key in obj) {
                    if (first) {
                        parameterStrings += key + "=" + decodeURIComponent(queryParameters[param][key]);
                        first = false;
                    }
                    else {
                        parameterStrings += "&" + key + "=" + decodeURIComponent(queryParameters[param][key]);
                    }
                }
            }
            return parameterStrings;
        }

        // append the strings together with '\n' as a delimiter
        var appendStrings = function (acceptType, date, host, path, queryParameters) {
            var stringBuilder = acceptType + "\n";
            stringBuilder += date + "\n";
            stringBuilder += host + "\n";
            stringBuilder += path + "\n";
            stringBuilder += queryParameters + "\n";
            return stringBuilder;
        }

        // compute the digest base on idString and the key via HmacSHA1 hash
        var buildDigest = function (key, idString) {
            // idString should be encoded in utf-8
            idString = UTF8.encode(idString);
            var signature = CryptoJS.HmacSHA1(idString, key);
            var encodedData = signature.toString(CryptoJS.enc.Base64);

            return encodedData;
        }

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

        var scientificDisciplines = ["Agriculture", "Engineering", "Medicine", "Mathematics", "Applied Sciences", "Architecture",
            "Biology", "Botany", "Chemistry", "Computer Science", "Environmental Sciences", "Physics", "Sciences",
            "Statistics", "Dentistry", "Forestry", "Public Health", "Veterinary Medicine", "Astronomy & Astrophysics",
            "Library & Information Science", "Meteorology & Climatology", "Military & Naval Science",
            "Anatomy & Physiology", "Botany", "Diet & Clinical Nutrition", "Ecology", "Geology", "Government",
            "Occupational Therapy & Rehabilitation", "Oceanography", "Physical Therapy",
            "Pharmacy\\, Therapeutics\\, & Pharmacology"];

        var scientificDisciplinesLow = ["agriculture", "engineering", "medicine", "mathematics", "applied Sciences", "architecture",
            "biology", "botany", "chemistry", "computer science", "environmental sciences", "physics", "sciences",
            "statistics", "dentistry", "forestry", "public health", "veterinary medicine", "astronomy & astrophysics",
            "library & information science", "meteorology & climatology", "military & naval science",
            "anatomy & physiology", "botany", "diet & clinical nutrition", "ecology", "geology", "government",
            "occupational therapy & rehabilitation", "oceanography", "physical therapy",
            "pharmacy\\, therapeutics\\, & pharmacology"];

        var nonScientificDisciplines = ["Anthropology", "Dance", "Drama", "Economics", "Education", "Film",
            "Geography", "History & Archaeology", "International Relations", "Journalism & Communications",
            "Languages & Literatures", "Law", "Music", "Nursing", "Parapsychology & Occult Sciences",
            "Philosophy", "Political Science", "Psychology", "Recreation & Sports", "Religion", "Social Sciences",
            "Social Welfare & Social Work", "Sociology & Social History", "Visual Arts", "Women's Studies", "Zoology"];

        // build the search query URL based on current params
        var summonsquery = function (sizePage, pageNumb) {
            var queryParameters = [];
            // ,:\()${} must be escaped (\), except for s.q and s.fq
            // +-&amp;|!(){}[]^"~*?:\ must be escaped in Lucene query string (if not Lucene oper.)

            // for combining scope filter - in particular from different fields: 
            // s.fq=(Discipline:Engineering) OR (ContentType:Standard) 

            var focusString = "";
            if (options.scope == 'scientific') {
                // do we already have a facet value filter on a discipline? if yes, we should not add more
                var scopeValid = true;

                if ($('.facetview_filterselected').length != 0) {
                    if (($('.facetview_filterselected[rel="discipline"]').length != 0) ||
                            ($('.facetview_filterselected[rel="Discipline"]').length != 0)) {
                        scopeValid = false;
                    }
                }

                if (scopeValid) {
                    // here we restrict the range of disciplines to be considered in the search
                    var scientificString = "";
                    var first = true;
                    for (var item in scientificDisciplines) {
                        if (first) {
                            scientificString += encodeURIComponent(scientificDisciplines[item]);
                            first = false;
                        }
                        else {
                            scientificString += ", " + encodeURIComponent(scientificDisciplines[item]);
                        }
                    }

                    var nonScientificString = "";
                    first = true;
                    for (var item in nonScientificDisciplines) {
                        if (first) {
                            nonScientificString += nonScientificDisciplines[item] + "";
                            first = false;
                        }
                        else {
                            nonScientificString += " OR " + nonScientificDisciplines[item] + "";
                        }
                    }

                    var quer = {"s.fvgf": "Discipline,or," + scientificString};
                    // we need to add certain content types: standards
                    //var quer = { "s.fq" : "Discipline:("+scientificString+")" +
                    //	" OR ContentType:(Standard OR "+encodeURIComponent("Student Thesis")+" OR Exam)" } ;
                    queryParameters.push(quer);
                }
            }

            if (options.fulltext) {
                var quer = {"s.fvf": "isFullText, true"};
                queryParameters.push(quer);
            }

            if (options.scholarly) {
                var quer = {"s.fvf": "isScholarly, true"};
                queryParameters.push(quer);
            }

            // applied filters
            var dateFilter = -1;
            var filterString = "";
            $('.facetview_filterselected', obj).each(function () {
                if ($(this).hasClass('facetview_facetrange')) {
                    var rel = options.facets[ $(this).attr('rel') ]['field'];
                    var range = $(this).attr('href');
                    var ind = range.indexOf('_');
                    if (ind != -1) {
                        var from_ = new Date(parseInt(range.substring(0, ind)));
                        var to_ = new Date(parseInt(range.substring(ind + 1, range.length)));

                        var from_month = from_.getMonth() + 1;
                        if (from_month < 10) {
                            from_month = '0' + from_month;
                        }
                        var to_month = to_.getMonth() + 1;
                        if (to_month < 10) {
                            to_month = '0' + to_month;
                        }
                        var from_day = from_.getDay() + 1;
                        if (from_day < 10) {
                            from_day = '0' + from_day;
                        }
                        var to_day = to_.getDay() + 1;
                        if (to_day < 10) {
                            to_day = '0' + to_day;
                        }

                        var from_str = from_.getFullYear() + '-' + from_month + '-' + from_day;
                        var to_str = to_.getFullYear() + '-' + to_month + '-' + to_day;

                        var quer = {"s.rf": rel + ',' + from_str + ":" + to_str};
                        queryParameters.push(quer);
                    }

                }
                else if (($(this).attr('rel').indexOf("Date") != -1) ||
                        ($(this).attr('rel').indexOf("date") != -1) ||
                        ($(this).attr('rel').indexOf("when") != -1)) {
                    //var rel = options.facets[ $(this).attr('rel') ]['field'];
                    var rel = $(this).attr('rel');
                    var obj = {'range': {}};
                    var from_ = parseInt($(this).attr('href'));
                    //var to_ = from_ + 365*24*60*60*1000 - 1;
                    var to_ = from_ + 365 * 24 * 60 * 60 * 1000;
                    var date_from = new Date(from_);
                    var date_to = new Date(to_);

                    var from_str = date_from.getFullYear();// + '-' + (date_from.getMonth()+1) + '-' + date_from.getDay();
                    var to_str = date_to.getFullYear();// + '-' + (date_to.getMonth()+1) + '-' + date_to.getDay();

                    var quer = {"s.rf": $(this).attr('rel') + ',' + from_str + ":" + to_str};
                    queryParameters.push(quer);
                }
                else {
                    var quer = {"s.fvf": $(this).attr('rel') +
                                "," + encodeURIComponent($(this).attr('href'))};
                    queryParameters.push(quer);
                }
            });

            // list of facets
            for (var facet in options.facets) {
                if ((options.facets[facet]['type'] == 'date')) {
                    var range = "";
                    if (dateFilter == -1) {
                        var max_date;
                        var min_date;
                        if (!options.max_date) {
                            max_date = 2012;
                        }
                        else {
                            max_date = options.max_date;
                        }
                        if (!options.min_date) {
                            min_date = 1000;
                        }
                        else {
                            min_date = options.min_date;
                        }

                        for (var i = max_date; ((i >= min_date) && (max_date - i < options.facets[facet]['size'])); i--) {
                            if (i == max_date) {
                                range = (max_date) + ":" + max_date;
                            }
                            else if (((i - 1) < min_date) || (max_date - (i - 1) == options.facets[facet]['size'])) {
                                range = min_date + ":" + i + "," + range;
                                break;
                            }
                            else {
                                range = (i) + ":" + i + "," + range;
                            }
                        }
                    }

                    var localFacet = {"s.rff": options.facets[facet]['field'] + "," + range};
                    queryParameters.push(localFacet);
                }
                else {
                    var localFacet = null;
                    if (options.facets[facet]['field'] == 'Discipline') {
                        localFacet = {"s.ff": options.facets[facet]['field'] + ",or,1," +
                                    options.facets[facet]['size'] + 0};
                    }
                    else {
                        localFacet = {"s.ff": options.facets[facet]['field'] + ",or,1," +
                                    options.facets[facet]['size']};
                    }
                    queryParameters.push(localFacet);
                }
            }

            if ($('#facetview_freetext1').val() ||
                    $('#facetview_freetext').val() ||
                    (filterString.length > 0) ||
                    (focusString.length > 0)) {
                //var queryText = encodeURI($('#facetview_freetext').val());
                var queryText = "";

                if (options['mode_query'] == 'complex') {
                    var rank = 1;
                    // first we build the query based on the search fields
                    for (rank = 1; rank <= options['complex_fields']; rank++) {
                        var modality = $('#label2_facetview_searchbar' + rank).text();

                        if ($('#facetview_freetext' + rank).val() != "") {
                            if (modality == 'must') {
                                queryText += " AND ";
                            }
                            else if (modality == 'should') {
                                queryText += " OR ";
                            }
                            else if (modality == 'must_not') {
                                query1Text += " NOT ";
                            }
                            if ($('#label1_facetview_searchbar' + rank).text() == "all text") {
                                queryText += " " + $('#facetview_freetext' + rank).val();
                            }
                            else if ($('#label1_facetview_searchbar' + rank).text() == "title") {
                                queryText += " (Title:" + $('#facetview_freetext' + rank).val() + ") ";
                            }
                            else if ($('#label1_facetview_searchbar' + rank).text() == "abstract") {
                                queryText += " (Abstract:" + $('#facetview_freetext' + rank).val() + ") ";
                            }
                            else if ($('#label1_facetview_searchbar' + rank).text() == "author") {
                                queryText += " (Author:" + $('#facetview_freetext' + rank).val() + ") ";
                            }
                        }
                    }

                    var delim = {"s.hs": "<strong>"};
                    queryParameters.push(delim);
                    delim = {"s.he": "</strong>"};
                    queryParameters.push(delim);
                    delim = {"s.hl": "true"};
                    queryParameters.push(delim);
                }
                else if ($('#facetview_freetext').val()) {
                    if (options['mode_query'] == 'nl') {
                        //queryText = $('#facetview_freetext').val();						
                        var myString = $('#facetview_freetext').val().replace(/\d+/g, '');
                        // and we must escape all lucene special characters: +-&amp;|!(){}[]^"~*?:\
                        var regex = RegExp('[' + lucene_specials.join('\\') + ']', 'g');
                        queryText = myString.replace(regex, "\\$&");
                    }
                    else {
                        // simple query
                        queryText = $('#facetview_freetext').val();
                    }
                    //var delim = { "s.cmd" : "setHighlightDelimiters(<strong>,</strong>)" };		
                    var delim = {"s.hs": "<strong>"};
                    queryParameters.push(delim);
                    delim = {"s.he": "</strong>"};
                    queryParameters.push(delim);
                    delim = {"s.hl": "true"};
                    queryParameters.push(delim);
                }

                var quer = null;
                if (filterString.trim().length > 0) {
                    if (queryText.trim().length > 0) {
                        queryText += " AND (" + filterString;
                        //queryText += " (" + filterString;
                    }
                    else {
                        queryText = "(" + filterString;
                    }
                    if (focusString.trim().length > 0) {
                        queryText += " AND (" + focusString + " )";
                        //queryText += " (" + focusString + " )";
                    }
                    queryText += " )";
                }

                if (queryText.trim().length > 0) {
                    quer = {"s.q": queryText};
                }
                queryParameters.push(quer);
            }

            if (sizePage != -1) {
                var pageS = {"s.ps": sizePage};
                queryParameters.push(pageS);
            }
            else {
                var pageS = {"s.ps": options.paging.size};
                queryParameters.push(pageS);
            }

            if (pageNumb != -1) {
                var pageNumbb = {"s.pn": pageNumb};
                queryParameters.push(pageNumbb);
            }
            else {
                var pageNumber = 1;
                if (options.paging.from > 0)
                    pageNumber = (options.paging.from / options.paging.size) + 1;

                var pageNumbb = {"s.pn": pageNumber};
                //var pageNumbb = { "s.cmd" : "goToPage("+pageNumber+")" };
                queryParameters.push(pageNumbb);

                //pageNumb = { "s.cmd" : "goToPage("+pageNumber+")" };
                //queryParameters.push(pageNumb);
            }

            var dym = {"s.dym": "true"};
            queryParameters.push(dym);

            return queryParameters;
        }

        // this is the list of Lucene characters to escape when not used as lucene operators:  +-&amp;|!(){}[]^"~*?:\
        var lucene_specials =
                ["-", "[", "]", "{", "}", "(", ")", "*", "+", "?", "\\", "^", "|", "&", "!", '"', "~", ":"];

        // build the search query URL based on current params
        var elasticsearchquery = function () {
            var qs = {};
            var bool = false;
            var should = false;
            var must_not = false;
            //var nested = false;
            var filtered = false; // true if a filter at least applies to the query
            var queried_fields = []; // list of queried fields for highlights   

            // fields to be returned
            if (options['collection'] == 'patent')
                qs['fields'] = textFieldsPatentReturned;
            else
                qs['fields'] = textFieldsNPLReturned;

            if (options['mode_query'] == 'complex') {
                var rank = 1;
                // first we build the query based on the search fields
                for (rank = 1; rank <= options['complex_fields']; rank++) {

                    // modality is one of must, should and must_not
                    var modality = $('#label2_facetview_searchbar' + rank).text();

                    if ($('#facetview_freetext' + rank).val() != "") {
                        // init bool clause
                        if (!bool) {
                            if (modality == 'must')
                                bool = {'must': []};
                            else if (modality == 'should')
                                bool = {'should': []};
                            else
                                bool = {'must_not': []};
                        }
                        else {
                            if (modality == 'must')
                                bool['must'] = [];
                            else if (modality == 'should')
                                bool['should'] = [];
                            else
                                bool['must_not'] = [];
                        }

                        if ($('#label1_facetview_searchbar' + rank).text() == "all text") {
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            obj['query_string']['query'] = $('#facetview_freetext' + rank).val();
                            queried_fields.push("_all");
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "all titles") {
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField;
                            if (options['collection'] == 'npl') {
                                theField = "$teiCorpus.$teiHeader.$fileDesc.$titleStmt.$title.";
                            }
                            else {
                                theField = "$teiCorpus.$teiHeader.$fileDesc.$titleStmt.$title.";
                            }
                            if (($('#label3_facetview_searchbar' + rank).text() == "all") ||
                                    ($('#label3_facetview_searchbar' + rank).text() == "lang")) {
                                theField += "\\*";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "de") {
                                theField += "$lang_de";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "fr") {
                                theField += "$lang_fr";
                            }
                            else {
                                theField += "$lang_en";
                            }
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "all abstracts") {
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField;
                            if (options['collection'] == 'npl') {
                                theField = "$teiCorpus.$teiHeader.$text.$front.$div.$p.";
                            }
                            else {
                                theField = "$teiCorpus.$teiHeader.$text.$front.$div.$p.";
                            }
                            if (($('#label3_facetview_searchbar' + rank).text() == "all") ||
                                    ($('#label3_facetview_searchbar' + rank).text() == "lang")) {
                                theField += "\\*";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "de") {
                                theField += "$lang_de";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "fr") {
                                theField += "$lang_fr";
                            }
                            else {
                                theField += "$lang_en";
                            }
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            obj['query_string']['analyze_wildcard'] = false;
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "claims") {
                            // this one is for patent only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$text.$body.$div.$div.";
                            if (($('#label3_facetview_searchbar' + rank).text() == "all") ||
                                    ($('#label3_facetview_searchbar' + rank).text() == "lang")) {
                                theField += "\\*";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "de") {
                                theField += "$lang_de";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "fr") {
                                theField += "$lang_fr";
                            }
                            else {
                                theField += "$lang_en";
                            }
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "description") {
                            // this one is for patent only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$text.$body.$div.$p.";
                            if (($('#label3_facetview_searchbar' + rank).text() == "all") ||
                                    ($('#label3_facetview_searchbar' + rank).text() == "lang")) {
                                theField += "\\*";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "de") {
                                theField += "$lang_de";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "fr") {
                                theField += "$lang_fr";
                            }
                            else {
                                theField += "$lang_en";
                            }
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            obj['query_string']['analyze_wildcard'] = false;
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "full text") {
                            // this one is for NPL only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$text.$body.$div.$p.";
                            if (($('#label3_facetview_searchbar' + rank).text() == "all") ||
                                    ($('#label3_facetview_searchbar' + rank).text() == "lang")) {
                                theField += "\\*";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "de") {
                                theField += "$lang_de";
                            }
                            else if ($('#label3_facetview_searchbar' + rank).text() == "fr") {
                                theField += "$lang_fr";
                            }
                            else {
                                theField += "$lang_en";
                            }
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            obj['query_string']['analyze_wildcard'] = false;
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "IPC class") {
                            // this one is for patent only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            //var theField = "$teiCorpus.$teiCorpus.$teiHeader.$profileDesc.$textClass.$classCode.$term";
                            var theField =
                                    "$teiCorpus.$teiHeader.$profileDesc.$textClass.$classCode.$scheme_ipc.$term";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "ECLA class") {
                            // this one is for patent only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$profileDesc.$textClass.$classCode.$scheme_patent-classification.$ecla.$path";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "ap. country") {
                            // this one is for patent only	 
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$list.$item.$list.$item.$country";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "authors\' country") {
                            // this one is for NPL only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$analytic.$author.$affiliation.$address.key";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "author") {
                            // this one is for NPL only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$analytic.$author.$persName.$surname";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "affiliation") {
                            // this one is for NPL only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$analytic.$author.$affiliation.$orgName";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "inventor") {
                            // this one is for NPL only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$list.$item.$type_parties.$listPerson.$person.$type_docdba.$persName";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }
                        else if ($('#label1_facetview_searchbar' + rank).text() == "applicant") {
                            // this one is for NPL only
                            var obj = {'query_string': {'default_operator': 'AND'}};
                            var theField = "$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$list.$item.$type_parties.$list.$item.$type_docdb.$name";
                            obj['query_string']['query'] =
                                    theField + ":" + $('#facetview_freetext' + rank).val();
                            queried_fields.push(theField);
                        }

                        // complete bool clause
                        if (modality == 'must')
                            bool['must'].push(obj);
                        else if (modality == 'should')
                            bool['should'].push(obj);
                        else
                            bool['must_not'].push(obj);
                    }
                }
                // we then create the filters which have been selected
                $('.facetview_filterselected', filtered).each(function () {
                    if (!filtered)
                        filtered = {'and': []};

                    if ($(this).hasClass('facetview_facetrange')) {
                        // facet filter for a range of values
                        var rel = options.facets[ $(this).attr('rel') ]['field'];
                        //var from_ = (parseInt( $('.facetview_lowrangeval', this).html() ) - 1970)* 365*24*60*60*1000;
                        //var to_ = (parseInt( $('.facetview_highrangeval', this).html() ) - 1970) * 365*24*60*60*1000 - 1;
                        var range = $(this).attr('href');
                        var ind = range.indexOf('_');
                        if (ind != -1) {
                            var from_ = range.substring(0, ind);
                            var to_ = range.substring(ind + 1, range.length);
                            var rngs = {
                                'from': "" + from_,
                                'to': "" + to_
                            }
                            var obbj = {'range': {}}
                            obbj['range'][ rel ] = rngs;
                            filtered['and'].push(obbj);
                        }
                    }
                    else if (($(this).attr('rel').indexOf("$date") != -1) ||
                            ($(this).attr('rel').indexOf("Date") != -1) ||
                            ($(this).attr('rel').indexOf("when") != -1)) {
                        // facet filter for date
                        var rel = $(this).attr('rel');
                        var obbj = {'range': {}}
                        var from_ = $(this).attr('href');
                        //var to_ = parseInt(from_) + 365*24*60*60*1000 - 1;
                        var to_ = parseInt(from_) + 365 * 24 * 60 * 60 * 1000;
                        var rngs = {
                            'from': from_,
                            'to': to_
                        }
                        obbj['range'][ rel ] = rngs;
                        filtered['and'].push(obbj);
                    }
                    else {
                        // other facet filter
                        var obbj = {'term': {}};
                        obbj['term'][ $(this).attr('rel') ] = $(this).attr('href');
                        filtered['and'].push(obbj);
                    }
                });
                if (bool) {
                    var obj = {'query': {}};
                    var obj2 = {'bool': bool};

                    if (filtered['and']) {
                        obj['query'] = obj2;
                        obj['filter'] = filtered;
                        var objj = {'filtered': {}};
                        objj['filtered'] = obj;
                        qs['query'] = objj;
                    }
                    else {
                        qs['query'] = obj2;
                    }
                }
                else {
                    if (filtered['and']) {
                        var obj2 = {'query': {}};
                        obj2['filter'] = filtered;
                        obj2['query'] = {'match_all': {}};
                        var objj = {'filtered': {}};
                        objj['filtered'] = obj2;
                        qs['query'] = objj;
                    }
                    else
                        qs['query'] = {'match_all': {}};
                    if (options['collection'] == 'patent') {
                        qs['sort'] = [{"$teiCorpus.$TEI.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$monogr.$date": {"order": "asc"}}];
                    }
                    else {
                        qs['sort'] = [{"$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$imprint.$date": {"order": "desc"}}];
                    }
                }
            }
            else if (options['mode_query'] == 'nl') {
                $('.facetview_filterselected', obj).each(function () {
                    //console.log($(this).attr('rel') );
                    !bool ? bool = {'must': []} : "";
                    if ($(this).hasClass('facetview_facetrange')) {
                        var rel = options.facets[ $(this).attr('rel') ]['field'];
                        //var from_ = (parseInt( $('.facetview_lowrangeval', this).html() ) - 1970)* 365*24*60*60*1000;
                        //var to_ = (parseInt( $('.facetview_highrangeval', this).html() ) - 1970) * 365*24*60*60*1000 - 1;
                        var range = $(this).attr('href');
                        var ind = range.indexOf('_');
                        if (ind != -1) {
                            var from_ = range.substring(0, ind);
                            var to_ = range.substring(ind + 1, range.length);
                            var rngs = {
                                'from': "" + from_,
                                'to': "" + to_
                            }
                            var obj = {'range': {}};
                            obj['range'][ rel ] = rngs;
                            bool['must'].push(obj);
                        }
                    }
                    else if (($(this).attr('rel').indexOf("$date") != -1) ||
                            ($(this).attr('rel').indexOf("Date") != -1) ||
                            ($(this).attr('rel').indexOf("when") != -1)) {
                        //var rel = options.facets[ $(this).attr('rel') ]['field'];
                        var rel = $(this).attr('rel');
                        var obj = {'range': {}}
                        var from_ = $(this).attr('href');
                        //var to_ = parseInt(from_) + 365*24*60*60*1000 - 1;
                        var to_ = parseInt(from_) + 365 * 24 * 60 * 60 * 1000;
                        var rngs = {
                            'from': from_,
                            'to': to_
                        }
                        obj['range'][ rel ] = rngs;
                        bool['must'].push(obj);
                    }
                    else {
                        var obj = {'term': {}};
                        obj['term'][ $(this).attr('rel') ] = $(this).attr('href');
                        bool['must'].push(obj);
                    }
                });
                for (var item in options.predefined_filters) {
                    // predefined filters to apply to all search and defined in the options
                    !bool ? bool = {'must': []} : "";
                    var obj = {'term': {}};
                    obj['term'][ item ] = options.predefined_filters[item];
                    bool['must'].push(obj);
                }
                var theField = "";
                var analys = "";
                if (($('#label_facetview_searchbar').text() == "all") ||
                        ($('#label_facetview_searchbar').text() == "lang")) {
                    theField += "*";
                    analys = "default";
                }
                else if ($('#label_facetview_searchbar').text() == "de") {
                    theField += "$lang_de";
                    analys = "german";
                }
                else if ($('#label_facetview_searchbar').text() == "fr") {
                    theField += "$lang_fr";
                    analys = "french";
                }
                else {
                    theField += "$lang_en";
                    analys = "english";
                }

                if (bool) {
                    // $('#facetview_freetext').val() != ""
                    //    ? bool['must'].push( {'query_string': { 'query': $('#facetview_freetext').val() } } )
                    //    : "";
                    var obj = {'query': {}};
                    var obj2 = {'bool': bool};

                    obj['query'] = obj2;
                    var obj4 = {'filter': obj};

                    if ($('#facetview_freetext').val() == "") {
                        obj4['query'] = {'match_all': {}};
                        if (options['collection'] == 'patent') {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$monogr.$date": {"order": "asc"}}];
                        }
                        else {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$fileDesc.$publicationStmt.$date": {"order": "desc"}}];
                        }
                    }
                    else {
                        var textLangFields = [];
                        var textFields;
                        if (options['collection'] == 'npl') {
                            textFields = textFieldNPL;
                        }
                        else {
                            textFields = textFieldPatents;
                        }

                        for (var ii = 0; ii < textFields.length; ii++) {
                            if (ii == 0) {
                                textLangFields[ii] = textFields[ii] + theField;
                            }
                            else {
                                textLangFields[ii] = textFields[ii] + theField;
                            }
                            queried_fields.push(textLangFields[ii]);
                        }
                        // we don't want numbers here
                        var myString = $('#facetview_freetext').val().replace(/\d+/g, '');
                        //var myString = $('#facetview_freetext').val();
                        // and we must escape all lucene special characters: +-&amp;|!(){}[]^"~*?:\
                        var regex = RegExp('[' + lucene_specials.join('\\') + ']', 'g');
                        myString = myString.replace(regex, "\\$&");

                        obj4['query'] = {'query_string': {'fields': textLangFields,
                                'query': myString, "use_dis_max": true, "analyzer": analys, 'default_operator': 'OR'}};
                    }
                    qs['query'] = {'filtered': obj4};
                    //qs['query'] = {'bool': bool}
                }
                else {
                    if ($('#facetview_freetext').val() == "") {
                        qs['query'] = {'match_all': {}};
                        if (options['collection'] == 'patent') {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$monogr.$date": {"order": "asc"}}];
                        }
                        else {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$imprint.$date": {"order": "desc"}}];
                        }
                    }
                    else {
                        var textLangFields = [];
                        var textFields;
                        if (options['collection'] == 'npl') {
                            textFields = textFieldNPL;
                        }
                        else {
                            textFields = textFieldPatents;
                        }

                        for (var ii = 0; ii < textFields.length; ii++) {
                            if (ii == 0) {
                                textLangFields[ii] = textFields[ii] + theField;
                            }
                            else {
                                textLangFields[ii] = textFields[ii] + theField;
                            }
                            queried_fields.push(textLangFields[ii]);
                        }

                        // we don't want numbers here
                        var myString = $('#facetview_freetext').val().replace(/\d+/g, '');
                        //var myString = $('#facetview_freetext').val();
                        // and we must escape all lucene special characters: +-&amp;|!(){}[]^"~*?:\
                        var regex = RegExp('[' + lucene_specials.join('\\') + ']', 'g');
                        myString = myString.replace(regex, "\\$&");

                        qs['query'] = {'query_string': {'fields': textLangFields,
                                'query': myString, "use_dis_max": true, "analyzer": analys, 'default_operator': 'OR'}};
                    }
                }
            }
            else {
                // simple query mode	
                $('.facetview_filterselected', obj).each(function () {
                    // facet filter for a range of values
                    !bool ? bool = {'must': []} : "";
                    if ($(this).hasClass('facetview_facetrange')) {
                        var rel = options.facets[ $(this).attr('rel') ]['field'];
                        //var from_ = (parseInt( $('.facetview_lowrangeval', this).html() ) - 1970)* 365*24*60*60*1000;
                        //var to_ = (parseInt( $('.facetview_highrangeval', this).html() ) - 1970) * 365*24*60*60*1000 - 1;
                        var range = $(this).attr('href');
                        var ind = range.indexOf('_');
                        if (ind != -1) {
                            var from_ = range.substring(0, ind);
                            var to_ = range.substring(ind + 1, range.length);
                            var rngs = {
                                'from': "" + from_,
                                'to': "" + to_
                            }
                            var obj = {'range': {}};
                            obj['range'][ rel ] = rngs;
                            bool['must'].push(obj);
                            filtered = true;
                        }
                    }
                    else if (($(this).attr('rel').indexOf("$date") != -1) ||
                            ($(this).attr('rel').indexOf("Date") != -1) ||
                            ($(this).attr('rel').indexOf("when") != -1)) {
                        /// facet filter for a date
                        var rel = $(this).attr('rel');
                        var obj = {'range': {}}
                        var from_ = $(this).attr('href');
                        //var to_ = parseInt(from_) + 365*24*60*60*1000 - 1;
                        var to_ = parseInt(from_) + 365 * 24 * 60 * 60 * 1000;
                        var rngs = {
                            'from': from_,
                            'to': to_
                        }
                        obj['range'][ rel ] = rngs;
                        bool['must'].push(obj);
                        filtered = true;
                    }
                    else {
                        // other facet filter 
                        var obj = {'term': {}};
                        obj['term'][ $(this).attr('rel') ] = $(this).attr('href');
                        bool['must'].push(obj);
                        filtered = true;
                    }
                });
                for (var item in options.predefined_filters) {
                    // predefined filters to apply to all search and defined in the options
                    !bool ? bool = {'must': []} : "";
                    var obj = {'term': {}};
                    obj['term'][ item ] = options.predefined_filters[item];
                    bool['must'].push(obj);
                    filtered = true;
                }
                if (bool) {
                    // $('#facetview_freetext').val() != ""
                    //    ? bool['must'].push( {'query_string': { 'query': $('#facetview_freetext').val() } } )
                    //    : "";
                    var obj = {'query': {}};
                    var obj2 = {'bool': bool};

                    /*if (nested) {
                     // case nested documents are queried 
                     obj['query'] = obj2;
                     // when nested documents are for the classes
                     obj['path'] = '$teiCorpus.$teiCorpus.$teiHeader.$profileDesc.$textClass';
                     // other cases in the future here...
                     
                     var obj3 = {'nested': obj};
                     var obj4 = {'filter': obj3};
                     }
                     else*/
                    {
                        // case no nested documents are queried
                        obj['query'] = obj2;
                        var obj4 = {'filter': obj};
                    }

                    if ($('#facetview_freetext').val() == "") {
                        obj4['query'] = {'match_all': {}};
                        if (options['collection'] == 'patent') {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$monogr.$date": {"order": "asc"}}];
                        }
                        else {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$imprint.$date": {"order": "desc"}}];
                        }
                    }
                    else
                        obj4['query'] = {'query_string': {'query': $('#facetview_freetext').val(), 'default_operator': 'AND'}};
                    qs['query'] = {'filtered': obj4};
                    //qs['query'] = {'bool': bool}
                }
                else {
                    if ($('#facetview_freetext').val() != "") {
                        qs['query'] = {'query_string': {'query': $('#facetview_freetext').val(), 'default_operator': 'AND'}}
                    }
                    else {
                        if (!filtered) {
                            qs['query'] = {'match_all': {}};
                        }
                        if (options['collection'] == 'patent') {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$fileDesc.$sourceDesc.$biblStruct.$monogr.$date": {"order": "asc"}}];
                        }
                        else {
                            qs['sort'] = [{"$teiCorpus.$teiHeader.$sourceDesc.$biblStruct.$monogr.$imprint.$date": {"order": "desc"}}];
                        }
                    }
                }
            }
            // set any paging
            options.paging.from != 0 ? qs['from'] = options.paging.from : ""
            options.paging.size != 10 ? qs['size'] = options.paging.size : ""

            // set any facets
            qs['facets'] = {};
            for (var item in options.facets) {
                var obj = jQuery.extend(true, {}, options.facets[item]);
                var nameFacet = obj['display'];
                delete obj['display'];

                if (options.facets[item]['type'] == 'date') {
                    obj['interval'] = "year";
                    //obj['size'] = 5; 
                    qs['facets'][nameFacet] = {"date_histogram": obj};
                }
                else {
                    obj['size'] = options.facets[item]['size'] + 50;
                    // this 50 is a magic number due to the following ES bug:
                    // https://github.com/elasticsearch/elasticsearch/issues/1305
                    // hopefully to be fixed in a near future
                    if (options.facets[item]['order'])
                        obj['order'] = options.facets[item]['order'];
                    else
                        obj['order'] = 'count';
                    // we need to remove type and view fields since ES 1.2
                    delete obj['type'];
                    delete obj['view'];
                    qs['facets'][nameFacet] = {"terms": obj};
                }
            }
            // set snippets/highlight
            if (queried_fields.length == 0) {
                queried_fields.push("_all");
            }
            qs['highlight'] = {};
            qs['highlight']['fields'] = {};
            for (var fie in queried_fields) {
                if (options['snippet_style'] == 'andlauer') {
                    qs['highlight']['fields'][queried_fields[fie]] = {'fragment_size': 130, 'number_of_fragments': 100};
                }
                else {
                    qs['highlight']['fields'][queried_fields[fie]] = {'fragment_size': 130, 'number_of_fragments': 3};
                }
            }
            qs['highlight']['order'] = 'score';
            qs['highlight']['pre_tags'] = ['<strong>'];
            qs['highlight']['post_tags'] = ['</strong>'];
            qs['highlight']['require_field_match'] = true;
            var theUrl = JSON.stringify(qs);

            if (window.console != undefined) {
                console.log(theUrl);
            }
            return theUrl;
        }

        // execute a search
        var dosearch = function () {
            // update the options with the latest q value
            options.q = $('#facetview_freetext').val();
            // make the search query
            if (options.search_index == "elasticsearch") {
                $.ajax({
                    type: "get",
                    url: options.search_url,
                    data: {source: elasticsearchquery()},
                    // processData: false,
                    dataType: "jsonp",
                    success: showresults
                });
            }
            else if (options.search_index == "summon") {
                var queryParameters = summonsquery(-1, -1);
                var header0 = authenticateSummons(queryParameters);
                queryParameters = summonsquery(-1, -1);
                var queryString = "";
                var first = true;
                for (var param in queryParameters) {
                    var obj = queryParameters[param];
                    for (var key in obj) {
                        if (first) {
                            queryString += key + '=' + queryParameters[param][key];
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
                        success: showresults
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
                        success: showresults
                    });
                }
            }
        }

        var harvest = function (event) {
            event.preventDefault()

            var modal = '<div class="modal" id="facetview_harvestmodal" style="max-width:800px;width:650px;"> \
                <div class="modal-header"> \
                <a class="facetview_removeharvest close">×</a> \
                <h3>Harvest the result records</h3> \
                </div> \
                <div class="modal-body"> \
				<form class="well">';

            modal += '<div class="control-group"> \
				<label class="control-label" for="input"><b>CouchDB instance</b></label> \
		 		<div class="controls"> \
				<input type="text" class="input-xxlarge" id="input_couchdb" value="localhost:5984"/> \
				</div></div>';

            modal += '<div class="control-group"> \
				<label class="control-label" for="input"><b>Database name</b></label> \
		 		<div class="controls"> \
				<input type="text" class="input-xxlarge" id="input_dbname" value="query' + Math.floor((Math.random() * 100000) + 1) + '"/> \
				</div></div>';

            modal += '<div class="control-group"> \
				<label class="control-label" for="input"><b>nb results</b></label> \
		 		<div class="controls"> \
				from <input type="text" class="input-small" id="input_from" value="0"/> to \
				<input type="text" class="input-small" id="input_to" value="' + options.data.found + '"/> \
				with window size <input type="text" class="input-small" id="step_size" value="' + 50 + '"/> \
				</div></div>';

            modal += '<div class="control-group"> \
				<label class="control-label" for="input"><b>Interval time</b></label> \
		 		<div class="controls"> \
				<input type="text" class="input-small" id="input_interval" value="3000"/> ms \
				</div></div>';

            modal += '</form> \
				      <div class="progress progress-striped progress-danger"> \
				          <div class="bar" id="harvest_progress" style="width:0%;"></div> \
                      </div> \
					  <div id="info_progress" /> \
			    </div> \
                <div class="modal-footer"> \
                <a id="facetview_doharvest" href="#" class="btn btn-danger" rel="">Launch</a> \
                <a class="facetview_removeharvest btn close">Close</a> \
                </div>';

            $('#facetview').append(modal);

            $('.facetview_removeharvest').bind('click', removeharvest);
            $('#facetview_doharvest').bind('click', doharvest);
            $('#facetview_harvestmodal').modal('show');
        }

        // remove the harvest modal from page
        var removeharvest = function (event) {
            event.preventDefault()
            $('#facetview_harvestmodal').modal('hide')
            $('#facetview_harvestmodal').remove()
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

        var disambiguate = function () {
            var queryText = $('#facetview_freetext').val();
            doexpand(queryText);
        }

        var disambiguateNERD = function () {
            var queryText = $('#facetview_freetext').val();
            doexpandNERD(queryText);
        }

        // call the NERD service and propose senses to the user for his query
        var doexpandNERD = function (queryText) {
            //var queryString = '{ "text" : "' + encodeURIComponent(queryText) +'", "shortText" : true }';
            var queryString = '{ "text" : "' + queryText + '", "shortText" : true }';
            var urlNERD = "http://" + options.host_nerd + ":" + options.port_nerd + "/processNERDQueryScience";
            $.ajax({
                type: "POST",
                url: urlNERD,
//				contentType: 'application/json',
//				contentType: 'charset=UTF-8',
//				dataType: 'jsonp',
                dataType: "text",
//				data: { text : encodeURIComponent(queryText) },
                data: queryString,
//				data: JSON.stringify( { text : encodeURIComponent(queryText) } ),
                success: showexpandNERD
            });
        }

        var showexpandNERD = function (sdata) {
            if (!sdata) {
                return;
            }
            console.log(sdata);

            var jsonObject = parseDisambNERD(sdata);

            console.log(jsonObject);

            $('#disambiguation_panel').empty();
            var piece = '<div class="mini-layout fluid" style="background-color:#F7EDDC;"> \
				   		 <div class="row-fluid"><div class="span11" style="width:95%;">';
            if (jsonObject['entities']) {
                piece += '<table class="table" style="width:100%;border:1px solid white;">';
                for (var sens in jsonObject['entities']) {
                    var entity = jsonObject['entities'][sens];
                    var domains = entity.domains;
                    if (domains && domains.length > 0) {
                        domain = domains[0].toLowerCase();
                    }
                    var type = entity.type;

                    var colorLabel = null;
                    if (type)
                        colorLabel = type;
                    else if (domains && domains.length > 0) {
                        colorLabel = domain;
                    }
                    else
                        colorLabel = entity.rawName;

                    var start = parseInt(entity.offsetStart, 10);
                    var end = parseInt(entity.offsetEnd, 10);

                    var subType = entity.subtype;
                    var conf = entity.nerd_score;
                    if (conf && conf.length > 4)
                        conf = conf.substring(0, 4);
                    var definitions = entity.definitions;
                    var wikipedia = entity.wikipediaExternalRef;
                    var freebase = entity.freeBaseExternalRef;
                    var content = entity.rawName; //$(this).text();
                    var preferredTerm = entity.preferredTerm;

                    piece += '<tr id="selectLine' + sens + '" href="'
                            + wikipedia + '" rel="$teiCorpus.$standoff.wikipediaExternalRef"><td id="selectArea' + sens + '" href="'
                            + wikipedia + '" rel="$teiCorpus.$standoff.wikipediaExternalRef">';
                    piece += '<div class="checkbox checkbox-inline checkbox-danger" id="selectEntityBlock' +
                            sens + '" href="' + wikipedia + '" rel="$teiCorpus.$standoff.wikipediaExternalRef">';
                    piece += '<input type="checkbox" id="selectEntity' + sens
                            + '" name="selectEntity' + sens + '" value="0" href="'
                            + wikipedia + '" rel="$TEI.$standoff.wikipediaExternalRef">';
                    piece += '<label for="selectEntity' + sens + '" id="label' + sens + '"> <strong>' + entity.rawName + '&nbsp;</strong> </label></div></td>';
                    //piece += '<td><strong>' + entity.rawName + '&nbsp;</strong></td><td>'+ 
                    if (preferredTerm) {
                        piece += '<td><b>' + preferredTerm + ': <b>' +
                                definitions[0]['definition']
                                + '</td><td>';
                    }
                    else {
                        piece += '<td>' +
                                definitions[0]['definition']
                                + '</td><td>';
                    }

                    if (freebase != null) {
                        var urlImage = 'https://usercontent.googleapis.com/freebase/v1/image' + freebase;
                        urlImage += '?maxwidth=150';
                        urlImage += '&maxheight=150';
                        urlImage += '&key=' + api_key;
                        piece += '<img src="' + urlImage + '" alt="' + freebase + '"/>';
                    }

                    piece += '</td><td>';

                    piece += '<table><tr><td>';

                    if (wikipedia) {
                        piece += '<a href="http://en.wikipedia.org/wiki?curid=' +
                                wikipedia +
                                '" target="_blank"><img style="max-width:28px;max-height:22px;" src="data/images/wikipedia.png"/></a>';
                    }
                    piece += '</td></tr><tr><td>';

                    if (freebase != null) {
                        piece += '<a href="http://www.freebase.com' +
                                freebase +
                                '" target="_blank"><img style="max-width:28px;max-height:22px;margin-top:5px;" src="data/images/freebase_icon.png"/></a>';

                    }
                    piece += '</td></tr></table>';

                    piece += '</td></tr>';
                }
                piece += '</table>';
            }

            /*for (var surf in jsonObject['paraphrases']) {
             piece += '<p>' + jsonObject['paraphrases'][surf] + '</p>';
             }*/

            piece += '</div><div><div id="close-disambiguate-panel" \
					  style="position:relative;float:right;" class="icon-remove icon-white"/></div></div></div>';
            $('#disambiguation_panel').append(piece);
            $('#close-disambiguate-panel').bind('click', function () {
                $('#disambiguation_panel').hide();
            })

            // we need to bind the checkbox...
            for (var sens in jsonObject['entities']) {
                $('input#selectEntity' + sens).bind('change', clickfilterchoice);
            }

            $('#disambiguation_panel').show();
        }

        var parseDisambNERD = function (sdata) {
            //var resObj = {};

            var jsonObject = JSON.parse(sdata);
            //var entities = jsonObject['entities'];

            return jsonObject;
        }

        // execute a query expansion
        var doexpand = function (queryText) {
            var header = authenticateIdilia(queryText);

            // query parameters
            var queryString = "text=" + encodeURIComponent(queryText);

            // there are three possible disambiguation recipe for queries: paidListings, search, productSearch
            //queryString += "&paraphrasingRecipe=productSearch";
            queryString += "&paraphrasingRecipe=search";
            queryString += "&maxCount=10";
            queryString += "&minWeight=0.0";
            queryString += "&textMime=" + encodeURIComponent("text/query; charset=utf8");
            queryString += "&timeout=200";
            queryString += "&wsdMime=" + encodeURIComponent("application/x-semdoc+xml");

            if (options.service == 'proxy') {
                // ajax service access via a proxy
                for (var param in header) {
                    var obj = header[param];
                    for (var key in obj) {
                        queryString += '&' + key + '=' + encodeURIComponent(header[param][key]);
                    }
                }

                var proxy = options.proxy_host + "/proxy-disambiguate.jsp?";
                $.ajax({
                    type: "get",
                    url: proxy,
                    contentType: 'application/json',
                    dataType: 'jsonp',
                    data: queryString,
                    success: showexpandpre
                });
            }
            else {
                // ajax service access is local
                $.ajax({
                    type: "get",
                    url: "http://api.idilia.com/1/text/paraphrase.mpjson?",
                    contentType: 'application/json',
//				   	dataType: 'json',
                    beforeSend: function (xhr) {
                        for (var param in header) {
                            var obj = header[param];
                            for (var key in obj) {
                                xhr.setRequestHeader(key, header[param][key]);
                            }
                        }
                    },
                    data: queryString,
                    success: showexpand
                });
            }
        }

        var showexpandpre = function (sdata) {
            showexpand(decodeURIComponent(sdata['content']));
        }

        var parseDisamb = function (sdata) {
            var resObj = {};
            resObj['paraphrases'] = [];

            var ind1 = sdata.indexOf("Content-Type: application/json");
            var ind10 = sdata.indexOf("{", ind1);
            var ind11 = sdata.indexOf("\n", ind10);

            var ind2 = sdata.indexOf("Content-Type: application/x-semdoc+xml");

            var jsonStr = sdata.substring(ind10, ind11);
            var jsonObject = JSON.parse(jsonStr);

            //console.log(jsonObject);

            for (var surf in jsonObject['paraphrases']) {
                resObj['paraphrases'].push(jsonObject['paraphrases'][surf]['surface']);
            }

            // we now parse the xml part
            var ind22 = sdata.indexOf("-------", ind2);
            var ind21 = sdata.indexOf("\n", ind2);
            var xmlStr = sdata.substring(ind21, ind22).trim();
            //console.log(xmlStr);

            resObj['senses'] = [];
            //for (var sens in $(xmlStr).find("sense")) {
            $('sense', xmlStr).each(function (i) {
                sens = $(this);
                //console.log(sens);
                var label = $(sens).attr("fsk");
                if (label) {
                    var ind = label.indexOf("/");
                    if (ind != -1) {
                        label = label.substring(0, ind)
                    }
                    label = label.replace('_', ' ');
                }
                var desc = $(sens).find("desc").text();
                var ref = $(sens).find("extRef");
                var wiki = null;
                var scope = true;
                if (ref) {
                    if (ref.text().indexOf("wikipedia") != -1) {
                        if ($(ref).find("ref")) {
                            wiki = $(ref).find("ref").text();
                        }
                    }
                    if ((ref.text().indexOf("mb_") != -1) ||
                            (sens.text().indexOf("musical_composition/N1") != -1)) {
                        scope = false;
                    }
                }
                if (scope) {
                    if (wiki) {
                        resObj['senses'].push({'label': label, 'desc': desc, 'wiki': wiki});
                    }
                    else {
                        resObj['senses'].push({'label': label, 'desc': desc});
                    }
                }
            });

            return resObj;
        }

        var showexpand = function (sdata) {
            if (!sdata) {
                return;
            }
            if (sdata.indexOf("----------") == -1) {
                return;
            }

            var jsonObject = parseDisamb(sdata);
            //console.log(jsonObject);

            $('#disambiguation_panel').empty();
            var piece = '<div class="mini-layout fluid" style="background-color:#F7EDDC;"> \
				   		 <div class="row-fluid"><div class="span11">';
            if (jsonObject['senses']) {
                piece += '<table class="table" style="width:100%;border:1px solid white;">';
                for (var sens in jsonObject['senses']) {

                    piece += '<tr><td><strong>' + jsonObject['senses'][sens]['label'] + '&nbsp;</strong></td><td>' +
                            jsonObject['senses'][sens]['desc']
                            + '</td><td>'
                    if (jsonObject['senses'][sens]['wiki']) {
                        piece += '<a href="http://en.wikipedia.org/wiki?curid=' +
                                jsonObject['senses'][sens]['wiki'] +
                                '" target="_blank"><img style="max-width:28px;max-height:22px;" src="data/images/wikipedia.png"/></a>';
                    }
                    piece += '</td></tr>';
                }
                piece += '</table>';
            }

            for (var surf in jsonObject['paraphrases']) {
                piece += '<p>' + jsonObject['paraphrases'][surf] + '</p>';
            }


            piece += '</div><div class="span1"><div id="close-disambiguate-panel" \
					  style="position:relative;float:right;" class="icon-remove icon-white"/></div></div></div>';
            $('#disambiguation_panel').append(piece);
            $('#close-disambiguate-panel').bind('click', function () {
                $('#disambiguation_panel').hide();
            })

            $('#disambiguation_panel').show();
        }

        // trigger a search when a filter choice is clicked
        var clickfilterchoice = function (event) {
            event.preventDefault();
//console.log(event);	
//console.log($(this));			
            if ($(this).html().trim().length == 0) {
                //if ($(this).type == 'checkbox') {	
                console.log('checkbox');
                if (!$(this).is(':checked')) {
                    //if (!$(this).checked) {	
                    console.log('checked');
                    $('.facetview_filterselected[href="' + $(this).attr("href") + '"]').each(function () {
                        $(this).remove();
                    });
                    options.paging.from = 0
                    dosearch();
                }
                else {
                    var newobj = '<a class="facetview_filterselected facetview_clear ' +
                            'btn btn-info" rel="' + $(this).attr("rel") +
                            '" alt="remove" title="remove"' +
                            ' href="' + $(this).attr("href") + '">';
                    if ($(this).html().trim().length > 0)
                        newobj += $(this).html().replace(/\(.*\)/, '');
                    else
                        newobj += $(this).attr("href");
                    newobj += ' <i class="icon-remove"></i></a>';
                    $('#facetview_selectedfilters').append(newobj);
                    $('.facetview_filterselected').unbind('click', clearfilter);
                    $('.facetview_filterselected').bind('click', clearfilter);
                    options.paging.from = 0
                    dosearch();
                }
            }
            else {
                var newobj = '<a class="facetview_filterselected facetview_clear ' +
                        'btn btn-info" rel="' + $(this).attr("rel") +
                        '" alt="remove" title="remove"' +
                        ' href="' + $(this).attr("href") + '">';
                if ($(this).html().trim().length > 0)
                    newobj += $(this).html().replace(/\(.*\)/, '');
                else
                    newobj += $(this).attr("href");
                newobj += ' <i class="icon-remove"></i></a>';
                $('#facetview_selectedfilters').append(newobj);
                $('.facetview_filterselected').unbind('click', clearfilter);
                $('.facetview_filterselected').bind('click', clearfilter);
                options.paging.from = 0
                dosearch();
            }
        }

        // clear a filter when clear button is pressed, and re-do the search
        var clearfilter = function (event) {
            event.preventDefault();
            $(this).remove();
            options.paging.from = 0
            dosearch();
        }

        var add_facet = function (event) {
            event.preventDefault();
            var truc = {'field': 'undefined', 'display': 'new_facet', 'size': 0, 'type': '', 'view': 'hidden'};
            options.facets.push(truc);
            buildfilters();
            dosearch();
        }

        var add_field = function (event) {
            event.preventDefault();
            var nb_fields = options['complex_fields'] + 1;
            $(this).parent().parent().append(field_complex.replace(/{{NUMBER}}/gi, '' + nb_fields)
                    .replace(/{{HOW_MANY}}/gi, options.paging.size));

            // bind the new thingies in the field
            $('#facetview_partial_match' + nb_fields).bind('click', fixmatch);
            $('#facetview_exact_match' + nb_fields).bind('click', fixmatch);
            $('#facetview_fuzzy_match' + nb_fields).bind('click', fixmatch);
            $('#facetview_match_any' + nb_fields).bind('click', fixmatch);
            $('#facetview_match_all' + nb_fields).bind('click', fixmatch);
            $('#facetview_howmany' + nb_fields).bind('click', howmany);

            $('#field_all_text' + nb_fields).bind('click', set_field);
            $('#field_title' + nb_fields).bind('click', set_field);
            $('#field_abstract' + nb_fields).bind('click', set_field);
            $('#field_claims' + nb_fields).bind('click', set_field);
            $('#field_description' + nb_fields).bind('click', set_field);
            $('#field_class_ipc' + nb_fields).bind('click', set_field);
            $('#field_class_ecla' + nb_fields).bind('click', set_field);
            $('#field_country' + nb_fields).bind('click', set_field);
            $('#field_author' + nb_fields).bind('click', set_field);
            $('#field_applicant' + nb_fields).bind('click', set_field);
            $('#field_inventor' + nb_fields).bind('click', set_field);

            $('#lang_all' + nb_fields).bind('click', set_field);
            $('#lang_en' + nb_fields).bind('click', set_field);
            $('#lang_de' + nb_fields).bind('click', set_field);
            $('#lang_fr' + nb_fields).bind('click', set_field);

            $('#must' + nb_fields).bind('click', set_field);
            $('#should' + nb_fields).bind('click', set_field);
            $('#must_not' + nb_fields).bind('click', set_field);

            options['complex_fields'] = nb_fields;

            // resize the new field
            thewidth = $('#facetview_searchbar' + nb_fields).parent().width();
            $('#facetview_searchbar' + nb_fields).css('width', (thewidth / 2) - 30 + 'px');
            $('#facetview_freetext' + nb_fields).css('width', (thewidth / 2) - 30 + 'px');

            // bind the new input field with the query callback
            $('#facetview_freetext' + nb_fields, obj).bindWithDelay('keyup', dosearch, options.freetext_submit_delay);
        }

        var set_field = function (event) {
            event.preventDefault();
            var theID = $(this).attr("rank");
            var labelID = $(this).attr("label");
            $('#label' + labelID + '_facetview_searchbar' + theID).empty();
            $('#label' + labelID + '_facetview_searchbar' + theID).append($(this).text());
            dosearch();
        }

        // do search options
        var fixmatch = function (event) {
            event.preventDefault();
            if ($(this).attr('id') == "facetview_partial_match") {
                var newvals = $('#facetview_freetext').val().replace(/"/gi, '').replace(/\*/gi, '').replace(/\~/gi, '').split(' ');
                var newstring = "";
                for (item in newvals) {
                    if (newvals[item].length > 0 && newvals[item] != ' ') {
                        if (newvals[item] == 'OR' || newvals[item] == 'AND') {
                            newstring += newvals[item] + ' ';
                        } else {
                            newstring += '*' + newvals[item] + '* ';
                        }
                    }
                }
                $('#facetview_freetext').val(newstring);
            }
            else if ($(this).attr('id') == "facetview_fuzzy_match") {
                var newvals = $('#facetview_freetext').val().replace(/"/gi, '').replace(/\*/gi, '').replace(/\~/gi, '').split(' ');
                var newstring = "";
                for (item in newvals) {
                    if (newvals[item].length > 0 && newvals[item] != ' ') {
                        if (newvals[item] == 'OR' || newvals[item] == 'AND') {
                            newstring += newvals[item] + ' ';
                        } else {
                            newstring += newvals[item] + '~ ';
                        }
                    }
                }
                $('#facetview_freetext').val(newstring);
            }
            else if ($(this).attr('id') == "facetview_exact_match") {
                var newvals = $('#facetview_freetext').val().replace(/"/gi, '').replace(/\*/gi, '').replace(/\~/gi, '').split(' ');
                var newstring = "";
                for (item in newvals) {
                    if (newvals[item].length > 0 && newvals[item] != ' ') {
                        if (newvals[item] == 'OR' || newvals[item] == 'AND') {
                            newstring += newvals[item] + ' ';
                        } else {
                            newstring += '"' + newvals[item] + '" ';
                        }
                    }
                }
                $.trim(newstring, ' ');
                $('#facetview_freetext').val(newstring);
            }
            else if ($(this).attr('id') == "facetview_match_all") {
                $('#facetview_freetext').val($.trim($('#facetview_freetext').val().replace(/ OR /gi, ' ')));
                $('#facetview_freetext').val($('#facetview_freetext').val().replace(/ /gi, ' AND '));
            }
            else if ($(this).attr('id') == "facetview_match_any") {
                $('#facetview_freetext').val($.trim($('#facetview_freetext').val().replace(/ AND /gi, ' ')));
                $('#facetview_freetext').val($('#facetview_freetext').val().replace(/ /gi, ' OR '));
            }
            $('#facetview_freetext').focus().trigger('keyup');
        }

        // adjust how many results are shown
        var howmany = function (event) {
            event.preventDefault()
            var newhowmany = prompt('Currently displaying ' + options.paging.size +
                    ' results per page. How many would you like instead?')
            if (newhowmany) {
                options.paging.size = parseInt(newhowmany)
                options.paging.from = 0
                $('#facetview_howmany').html('results per page (' + options.paging.size + ')')
                dosearch();
            }
        }

        // the facet view object to be appended to the page
        var thefacetview_simple = ' \
           <div id="facetview"> \
             <div class="row-fluid"> \
               <div class="span3"> \
                 <div id="facetview_filters"></div> \
               </div> \
               <div class="span9" id="facetview_rightcol" style="position:relative; left:0px; margin-left:5px; margin-right:0px; "> \
                   <div id="facetview_searchbar" style="display:inline; float:left;" class="input-prepend"> \
                   <span class="add-on"><i class="icon-search"></i></span> \
                   <input class="span4" id="facetview_freetext" name="q" value="" placeholder="search term" autofocus /> \
                   </div> \
                   <div style="display:inline; float:left;margin-left:-2px;" class="btn-group"> \
                    <a style="-moz-border-radius:0px 3px 3px 0px; \
                    -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
                    class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
                    <i class="icon-cog"></i> <span class="caret"></span></a> \
                    <ul style="margin-left:-110px;" class="dropdown-menu"> \
                    <li><a id="facetview_partial_match" href="">partial match</a></li> \
                    <li><a id="facetview_exact_match" href="">exact match</a></li> \
                    <li><a id="facetview_fuzzy_match" href="">fuzzy match</a></li> \
                    <li><a id="facetview_match_all" href="">match all</a></li> \
                    <li><a id="facetview_match_any" href="">match any</a></li> \
                    <li><a href="#">clear all</a></li> \
                    <li class="divider"></li> \
                    <li><a target="_blank" \
                    href="http://lucene.apache.org/java/2_9_1/queryparsersyntax.html"> \
                    query syntax doc.</a></li> \
                    <li class="divider"></li> \
                    <li><a id="facetview_howmany" href="#">results per page ({{HOW_MANY}})</a></li> \
                    </ul> \
                   </div> \
				   <div class-"span2" id="disambiguate_button"> \
				   <button type="button" id="disambiguate" class="btn" disabled="true" data-toggle="button">Disamb./Expand</button> \
				   <button type="button" id="harvest" class="btn" disabled="true" data-toggle="button">Harvest</button> \
				   </div> \
                   <div style="clear:both;" id="facetview_selectedfilters"></div> \
				   <div class="span5" id="results_summary"></div> \
				   <div class="span9" id="disambiguation_panel" style="margin-left:5px;"></div> \
                 <table class="table table-striped" id="facetview_results"></table> \
                 <div id="facetview_metadata"></div> \
               </div> \
             </div> \
           </div> \
           ';

        // the facet view object to be appended to the page
        var thefacetview_nl = ' \
           <div id="facetview"> \
             <div class="row-fluid"> \
               <div class="span3"> \
                 <div id="facetview_filters"></div> \
               </div> \
               <div class="span9" id="facetview_rightcol" style="position:relative; left:0px;"> \
                   <div id="facetview_searchbar" style="display:inline; float:left;" class="input-prepend"> \
                   <span class="add-on"><i class="icon-search"></i></span> \
				<div style="display:inline-block;margin-left:-5px;" class="btn-group"> \
				    <a style="-moz-border-radius:0px 3px 3px 0px; \
			       -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
				   class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
			       <b><span id="label_facetview_searchbar">lang</span></b> <span class="caret"></span></a> \
					<ul style="margin-left:-10px;" class="dropdown-menu"> \
				   <li><a id="lang_all" label="" rank="" href="">all</a></li> \
			       <li><a id="lang_en" label="" rank="" href="">en</a></li> \
			       <li><a id="lang_de" label="" rank="" href="">de</a></li> \
				   <li><a id="lang_fr" label="" rank="" href="">fr</a></li> \
				   </div> \
                   <textarea class="span4" id="facetview_freetext" name="q" value="" placeholder="search text" autofocus /> \
                   </div> \
                   <div style="display:inline; float:left;margin-left:-2px;bottom:-50px;" class="btn-group"> \
                   <div style="clear:both;" id="facetview_selectedfilters"></div> \
				   <div class="span5" id="results_summary"></div> \
                 <table class="table table-striped" id="facetview_results"></table> \
                 <div id="facetview_metadata"></div> \
               </div> \
             </div> \
           </div> \
           ';

        var field_complex;
        if (options.search_index == 'summon') {
            field_complex = ' \
			<div style="display:inline-block; margin-left:-2px;" class="btn-group"> \
			    <a style="-moz-border-radius:0px 3px 3px 0px; \
		       -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
		       class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
		       <b><span id="label1_facetview_searchbar{{NUMBER}}">select field</span></b> <span class="caret"></span></a> \
				<ul style="margin-left:-10px;" class="dropdown-menu"> \
		       <li><a id="field_all_text{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">all text</a></li> \
			   <li><a id="field_title{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">title</a></li> \
			   <li><a id="field_abstract{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">abstract</a></li> \
			   <li><a id="field_author{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">author</a></li> \
			   </div>';
        }
        else if (options['collection'] == 'npl') {
            field_complex = ' \
			<div style="display:inline-block; margin-left:-2px;" class="btn-group"> \
			    <a style="-moz-border-radius:0px 3px 3px 0px; \
		       -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
		       class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
		       <b><span id="label1_facetview_searchbar{{NUMBER}}">select field</span></b> <span class="caret"></span></a> \
				<ul style="margin-left:-10px;" class="dropdown-menu"> \
		       <li><a id="field_all_text{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">all text</a></li> \
			   <li><a id="field_title{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">all titles</a></li> \
			   <li><a id="field_abstract{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">all abstracts</a></li> \
			   <li><a id="field_fulltext{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">full text</a></li> \
			   <li><a id="field_author{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">author</a></li> \
			   <li><a id="field_affiliation{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">affiliation</a></li> \
			   <li><a id="field_country{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">authors\' country</a></li> \
			   </div>'
        }
        else {
            field_complex = ' \
			<div style="display:inline-block; margin-left:-2px;" class="btn-group"> \
			    <a style="-moz-border-radius:0px 3px 3px 0px; \
		       -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
		       class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
		       <b><span id="label1_facetview_searchbar{{NUMBER}}">select field</span></b> <span class="caret"></span></a> \
				<ul style="margin-left:-10px;" class="dropdown-menu"> \
		       <li><a id="field_all_text{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">all text</a></li> \
			   <li><a id="field_title{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">all titles</a></li> \
			   <li><a id="field_abstract{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">all abstracts</a></li> \
			   <li><a id="field_claims{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">claims</a></li> \
			   <li><a id="field_description{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">description</a></li> \
		       <li><a id="field_class_ipc{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">IPC class</a></li> \
			   <li><a id="field_class_ecla{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">ECLA class</a></li> \
			   <li><a id="field_country{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">ap. country</a></li> \
			   <li><a id="field_inventor{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">inventor</a></li> \
			   <li><a id="field_applicant{{NUMBER}}" rank="{{NUMBER}}" label="1" href="">applicant</a></li> \
			   </div>'
        }
        field_complex += '<div style="display:inline-block;margin-left:-5px;" class="btn-group"> \
			    <a style="-moz-border-radius:0px 3px 3px 0px; \
		       -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
			   class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
		       <b><span id="label3_facetview_searchbar{{NUMBER}}">lang</span></b> <span class="caret"></span></a> \
				<ul style="margin-left:-10px;" class="dropdown-menu"> \
			   <li><a id="lang_all{{NUMBER}}" rank="{{NUMBER}}" label="3" href="">all</a></li> \
		       <li><a id="lang_en{{NUMBER}}" rank="{{NUMBER}}" label="3" href="">en</a></li> \
		       <li><a id="lang_de{{NUMBER}}" rank="{{NUMBER}}" label="3" href="">de</a></li> \
			   <li><a id="lang_fr{{NUMBER}}" rank="{{NUMBER}}" label="3" href="">fr</a></li> \
			   </div> \
			<div style="display:inline-block;;margin-left:-5px;" class="btn-group"> \
			    <a style="-moz-border-radius:0px 3px 3px 0px; \
		       -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
		       class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
		       <b><span id="label2_facetview_searchbar{{NUMBER}}">should</span></b> <span class="caret"></span></a> \
				<ul style="margin-left:-10px;" class="dropdown-menu"> \
		       <li><a id="must{{NUMBER}}" rank="{{NUMBER}}" label="2" href="">must</a></li> \
		       <li><a id="should{{NUMBER}}" rank="{{NUMBER}}" label="2" href="">should</a></li> \
			   <li><a id="must_not{{NUMBER}}" rank="{{NUMBER}}" label="2" href="">must_not</a></li> \
			   </div> \
		      <div id="facetview_searchbar{{NUMBER}}" style="margin-left:-5px; position:relative; top:-7px; display:inline-block;"> \
		       <input class="span4" id="facetview_freetext{{NUMBER}}" name="q" value="" placeholder="search term" /> \
		       </div> \
		       <div style="display:inline-block; margin-left:-2px;" class="btn-group"> \
		        <a style="-moz-border-radius:0px 3px 3px 0px; \
		        -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
		        class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
		        <i class="icon-cog"></i> <span class="caret"></span></a> \
		        <ul style="margin-left:-110px;" class="dropdown-menu"> \
		        <li><a id="facetview_partial_match{{NUMBER}}" href="">partial match</a></li> \
		        <li><a id="facetview_exact_match{{NUMBER}}" href="">exact match</a></li> \
		        <li><a id="facetview_fuzzy_match{{NUMBER}}" href="">fuzzy match</a></li> \
		        <li><a id="facetview_match_all{{NUMBER}}" href="">match all</a></li> \
		        <li><a id="facetview_match_any{{NUMBER}}" href="">match any</a></li> \
		        <li><a href="#">clear all</a></li> \
		        <li class="divider"></li> \
		        <li><a target="_blank" \
		        href="http://lucene.apache.org/java/2_9_1/queryparsersyntax.html"> \
		        query syntax doc.</a></li> \
		        <li class="divider"></li> \
		        <li><a id="facetview_howmany{{NUMBER}}" href="#">results per page ({{HOW_MANY}})</a></li> \
		        </ul> \
		       </div> \
			   <br/> \
			';

        var thefacetview_complex = ' \
           <div id="facetview"> \
             <div class="row-fluid"> \
               <div class="span3"> \
                 <div id="facetview_filters"></div> \
               </div> \
               <div class="span9" id="facetview_rightcol" style="position:relative; left:0px;"> \
				 <div id="search_form">	\
					<div style="margin-left:-2px;margin-bottom:10px;" id="facetview_fieldbuttons" class="btn-group"> \
       	        	<a style="text-align:left; min-width:20%;" class="btn" \
	                 		id="new_field" href=""> \
	                 		<i class="icon-plus"></i> add new search field </a> \
					</div> ' +
                field_complex.replace(/{{NUMBER}}/gi, "1") +
                '</div> \
			      <div style="clear:both;" id="facetview_selectedfilters"></div> \
				  <div class="span5" id="results_summary"></div> \
	              <table class="table table-striped" id="facetview_results"></table> \
	              <div id="facetview_metadata"></div> \
				</div> \
             </div> \
           </div> \
           ';

        var thefacetview_epoque = ' \
           <div id="facetview"> \
			<span style="position:relative; top:-10px;"><a href="#?mode_query=simple">Simple</a> - <a href="#?mode_query=complex">Complex</a> - <a href="#?mode_query=nl">NL</a> - Epoque</span> \
             <div class="row-fluid"> \
               <div class="span3"> \
                 <div id="facetview_filters"></div> \
               </div> \
               <div class="span9" id="facetview_rightcol" style="position:relative; left:-20px;"> \
                   <div id="facetview_searchbar" style="display:inline; float:left;" class="input-prepend"> \
                   <span class="add-on"><i class="icon-search"></i></span> \
                   <input class="span4" id="facetview_freetext" name="q" value="" placeholder="search term" autofocus /> \
                   </div> \
                   <div style="display:inline; float:left;margin-left:-2px;" class="btn-group"> \
                    <a style="-moz-border-radius:0px 3px 3px 0px; \
                    -webkit-border-radius:0px 3px 3px 0px; border-radius:0px 3px 3px 0px;" \
                    class="btn dropdown-toggle" data-toggle="dropdown" href="#"> \
                    <i class="icon-cog"></i> <span class="caret"></span></a> \
                    <ul style="margin-left:-110px;" class="dropdown-menu"> \
                    <li><a id="facetview_partial_match1" href="">partial match</a></li> \
                    <li><a id="facetview_exact_match1" href="">exact match</a></li> \
                    <li><a id="facetview_fuzzy_match1" href="">fuzzy match</a></li> \
                    <li><a id="facetview_match_all1" href="">match all</a></li> \
                    <li><a id="facetview_match_any1" href="">match any</a></li> \
                    <li><a href="#">clear all</a></li> \
                    <li class="divider"></li> \
                    <li><a target="_blank" \
                    href="http://lucene.apache.org/java/2_9_1/queryparsersyntax.html"> \
                    query syntac doc.</a></li> \
                    <li class="divider"></li> \
                    <li><a id="facetview_howmany" href="#">results per page ({{HOW_MANY}})</a></li> \
                    </ul> \
                   </div> \
                   <div style="clear:both;" id="facetview_selectedfilters"></div> \
                 <table class="table table-striped" id="facetview_results"></table> \
                 <div id="facetview_metadata"></div> \
               </div> \
             </div> \
           </div> \
           ';

        // what to do when ready to go
        var whenready = function () {
            // append the facetview object to this object
            var thefacetview;
            if (options['mode_query'] == 'simple') {
                thefacetview = thefacetview_simple;
            }
            else if (options['mode_query'] == 'epoque') {
                thefacetview = thefacetview_epoque;
            }
            else if (options['mode_query'] == 'nl') {
                thefacetview = thefacetview_nl;
            }
            else {
                thefacetview = thefacetview_complex;
            }

            thefacetview = thefacetview.replace(/{{HOW_MANY}}/gi, options.paging.size);
            $(obj).append(thefacetview);

            if (options['mode_query'] == 'complex') {
                // setup default search option triggers
                $('#facetview_partial_match1').bind('click', fixmatch);
                $('#facetview_exact_match1').bind('click', fixmatch);
                $('#facetview_fuzzy_match1').bind('click', fixmatch);
                $('#facetview_match_any1').bind('click', fixmatch);
                $('#facetview_match_all1').bind('click', fixmatch);
                $('#facetview_howmany1').bind('click', howmany);

                $('#field_all_text1').bind('click', set_field);
                $('#field_title1').bind('click', set_field);
                $('#field_abstract1').bind('click', set_field);
                $('#field_claims1').bind('click', set_field);
                $('#field_description1').bind('click', set_field);
                $('#field_fulltext1').bind('click', set_field);
                $('#field_class_ipc1').bind('click', set_field);
                $('#field_class_ecla1').bind('click', set_field);
                $('#field_country1').bind('click', set_field);
                $('#field_affiliation1').bind('click', set_field);
                $('#field_author1').bind('click', set_field);
                $('#field_inventor1').bind('click', set_field);
                $('#field_applicant1').bind('click', set_field);

                $('#lang_all1').bind('click', set_field);
                $('#lang_en1').bind('click', set_field);
                $('#lang_de1').bind('click', set_field);
                $('#lang_fr1').bind('click', set_field);

                $('#must1').bind('click', set_field);
                $('#should1').bind('click', set_field);
                $('#must_not1').bind('click', set_field);

                $('#new_field').bind('click', add_field);
                options['complex_fields'] = 1;
            }
            else if (options['mode_query'] == 'nl') {
                $('#lang_all').bind('click', set_field);
                $('#lang_en').bind('click', set_field);
                $('#lang_de').bind('click', set_field);
                $('#lang_fr').bind('click', set_field);

                $('#facetview_partial_match').bind('click', fixmatch);
                $('#facetview_exact_match').bind('click', fixmatch);
                $('#facetview_fuzzy_match').bind('click', fixmatch);
                $('#facetview_match_any').bind('click', fixmatch);
                $('#facetview_match_all').bind('click', fixmatch);
                $('#facetview_howmany').bind('click', howmany);
            }
            else {
                // setup search option triggers
                $('#facetview_partial_match').bind('click', fixmatch);
                $('#facetview_exact_match').bind('click', fixmatch);
                $('#facetview_fuzzy_match').bind('click', fixmatch);
                $('#facetview_match_any').bind('click', fixmatch);
                $('#facetview_match_all').bind('click', fixmatch);
                $('#facetview_howmany').bind('click', howmany);
            }

            // resize the searchbar
            if (options['mode_query'] == 'complex') {
                thewidth = $('#facetview_searchbar1').parent().width();
                $('#facetview_searchbar1').css('width', (thewidth / 2) - 30 + 'px');
                $('#facetview_freetext1').css('width', (thewidth / 2) - 30 + 'px');
            }
            if (options['mode_query'] == 'nl') {
                thewidth = $('#facetview_searchbar').parent().width();
                $('#facetview_searchbar').css('width', (thewidth / 2) + 70 + 'px');
                $('#facetview_freetext').css('width', (thewidth / 1.5) - 20 + 'px');

                var theheight = $('#facetview_searchbar').parent().height();
                $('#facetview_searchbar').css('height', (theheight) - 20 + 'px');
                $('#facetview_freetext').css('height', (theheight) - 20 + 'px');
            }
            else {
                var thewidth = $('#facetview_searchbar').parent().width();
                $('#facetview_searchbar').css('width', thewidth / 2 + 70 + 'px'); // -50
                $('#facetview_freetext').css('width', thewidth / 2 + 32 + 'px'); // -88

                //$('#disambiguate').bind('click',disambiguate);
                $('#disambiguate').bind('click', disambiguateNERD);
                $('#disambiguation_panel').hide();

                if (options.search_index == "summon") {
                    $('#harvest').bind('click', harvest);
                }
                else {
                    $('#harvest').hide();
                }
            }
            // check paging info is available
            !options.paging.size ? options.paging.size = 10 : "";
            !options.paging.from ? options.paging.from = 0 : "";

            // set any default search values into the search bar
            $('#facetview_freetext').val() == "" && options.q != "" ? $('#facetview_freetext').val(options.q) : ""

            // append the filters to the facetview object
            buildfilters();

            if (options['mode_query'] == 'complex') {
                $('#facetview_freetext1', obj).bindWithDelay('keyup', dosearch, options.freetext_submit_delay);
            }
            else {
                $('#facetview_freetext', obj).bindWithDelay('keyup', dosearch, options.freetext_submit_delay);
                $('#facetview_freetext', obj).bind('keyup', activateDisambButton);
                $('#facetview_freetext', obj).bind('keyup', activateHarvestButton);
            }

            // trigger the search once on load, to get all results
            dosearch();
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

        // ===============================================
        // now create the plugin on the page
        return this.each(function () {
            // get this object
            obj = $(this);

            // check for remote config options, then do first search
            if (options.config_file) {
                $.ajax({
                    type: "get",
                    url: options.config_file,
                    dataType: "jsonp",
                    success: function (data) {
                        options = $.extend(options, data)
                        whenready();
                    },
                    error: function () {
                        $.ajax({
                            type: "get",
                            url: options.config_file,
                            success: function (data) {
                                options = $.extend(options, $.parseJSON(data))
                                whenready();
                            },
                            error: function () {
                                whenready();
                            }
                        })
                    }
                })
            }
            else {
                whenready();
            }
        }); // end of the function  

    };


    var Base64 = {
        // private property
        _keyStr: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
        // public method for encoding
        encode: function (input) {
            var output = "";
            var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
            var i = 0;

            input = Base64._utf8_encode(input);

            while (i < input.length) {

                chr1 = input.charCodeAt(i++);
                chr2 = input.charCodeAt(i++);
                chr3 = input.charCodeAt(i++);

                enc1 = chr1 >> 2;
                enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
                enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
                enc4 = chr3 & 63;

                if (isNaN(chr2)) {
                    enc3 = enc4 = 64;
                } else if (isNaN(chr3)) {
                    enc4 = 64;
                }

                output = output +
                        this._keyStr.charAt(enc1) + this._keyStr.charAt(enc2) +
                        this._keyStr.charAt(enc3) + this._keyStr.charAt(enc4);
            }

            return output;
        },
        // public method for decoding
        decode: function (input) {
            var output = "";
            var chr1, chr2, chr3;
            var enc1, enc2, enc3, enc4;
            var i = 0;

            input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");

            while (i < input.length) {

                enc1 = this._keyStr.indexOf(input.charAt(i++));
                enc2 = this._keyStr.indexOf(input.charAt(i++));
                enc3 = this._keyStr.indexOf(input.charAt(i++));
                enc4 = this._keyStr.indexOf(input.charAt(i++));

                chr1 = (enc1 << 2) | (enc2 >> 4);
                chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
                chr3 = ((enc3 & 3) << 6) | enc4;

                output = output + String.fromCharCode(chr1);

                if (enc3 != 64) {
                    output = output + String.fromCharCode(chr2);
                }
                if (enc4 != 64) {
                    output = output + String.fromCharCode(chr3);
                }

            }

            output = Base64._utf8_decode(output);

            return output;

        },
        // private method for UTF-8 encoding
        _utf8_encode: function (string) {
            string = string.replace(/\r\n/g, "\n");
            var utftext = "";

            for (var n = 0; n < string.length; n++) {

                var c = string.charCodeAt(n);

                if (c < 128) {
                    utftext += String.fromCharCode(c);
                }
                else if ((c > 127) && (c < 2048)) {
                    utftext += String.fromCharCode((c >> 6) | 192);
                    utftext += String.fromCharCode((c & 63) | 128);
                }
                else {
                    utftext += String.fromCharCode((c >> 12) | 224);
                    utftext += String.fromCharCode(((c >> 6) & 63) | 128);
                    utftext += String.fromCharCode((c & 63) | 128);
                }

            }

            return utftext;
        },
        // private method for UTF-8 decoding
        _utf8_decode: function (utftext) {
            var string = "";
            var i = 0;
            var c = c1 = c2 = 0;

            while (i < utftext.length) {

                c = utftext.charCodeAt(i);

                if (c < 128) {
                    string += String.fromCharCode(c);
                    i++;
                }
                else if ((c > 191) && (c < 224)) {
                    c2 = utftext.charCodeAt(i + 1);
                    string += String.fromCharCode(((c & 31) << 6) | (c2 & 63));
                    i += 2;
                }
                else {
                    c2 = utftext.charCodeAt(i + 1);
                    c3 = utftext.charCodeAt(i + 2);
                    string += String.fromCharCode(((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63));
                    i += 3;
                }

            }

            return string;
        }

    }

    UTF8 = {
        encode: function (s) {
            for (var c, i = -1, l = (s = s.split("")).length, o = String.fromCharCode; ++i < l;
                    s[i] = (c = s[i].charCodeAt(0)) >= 127 ? o(0xc0 | (c >>> 6)) + o(0x80 | (c & 0x3f)) : s[i]
                    )
                ;
            return s.join("");
        },
        decode: function (s) {
            for (var a, b, i = -1, l = (s = s.split("")).length, o = String.fromCharCode, c = "charCodeAt"; ++i < l;
                    ((a = s[i][c](0)) & 0x80) &&
                    (s[i] = (a & 0xfc) == 0xc0 && ((b = s[i + 1][c](0)) & 0xc0) == 0x80 ?
                            o(((a & 0x03) << 6) + (b & 0x3f)) : o(128), s[++i] = "")
                    )
                ;
            return s.join("");
        }
    };

    var googleKey = "AIzaSyBLNMpXpWZxcR9rbjjFQHn_ULbU-w1EZ5U";

    function NERTypeMapping(type, def) {
        var label = null;
        switch (type) {
            case "location/N1":
                label = "location";
                break;
            case "event/N1":
                label = "event";
                break;
            case "time_period/N1":
                label = "period";
                break;
            case "person/N1":
                label = "person";
                break;
            case "national/J3":
                label = "national";
                break;
            case "acronym/N1":
                label = "acronym";
                break;
            case "institution/N2":
                label = "institution";
                break;
            case "measure/N3":
                label = "measure";
                break;
            case "organizational_unit/N1":
                label = "organization";
                break;
            case "title/N6":
                label = "title";
                break;
            case "artifact/N1":
                label = "artifact";
                break;
            default:
                label = def;
        }
        return label;
    }

    // facetview options are declared as a function so that they can be retrieved
    // externally (which allows for saving them remotely etc.)
    $.fn.facetview.options = {};

})(jQuery);


