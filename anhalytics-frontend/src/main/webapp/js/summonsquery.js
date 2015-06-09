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