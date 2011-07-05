(function(window, undefined){

	if(!window.Kininaru){
		Kininaru = {};
	}

	// utils {{{
	var isNull = function(x){ return (x === undefined || x === null); },
		isBlank = function(x){ return(isNull(x) || x === ""); },
		isNotNull = function(x){ return !isNull(x); },
		isNotBlank = function(x){ return !isBlank(x); },
		range = function(start, end){
			var res = [];
			for(var i = start; i < end; ++i){ res.push(i); }
			return res; };
	// }}}

	Kininaru.loaderImage = "<img src='/img/ajax-loader.gif' />";

	Kininaru.total = -1;
	Kininaru.page = 1;
	Kininaru.booksPerPage = 10;

	Kininaru.speed = 500;

	Kininaru.targetURL = "/kininaru/my?";
	Kininaru.showUser = false;

	//Kininaru.template = {
	//	kininaru: $.createTemplateURL("/static/kininaru.tpl")
	//};

	Kininaru.hidePager = function(){
		$("#pager").hide();
		$("#prev").hide();
		$("#next").hide();
	};


	Kininaru.getKey = function(obj){ return((obj.key) ? obj.key : obj.userkey); };

	Kininaru.pager = function(bookCount){
		var showPager = false;
		if(this.page > 1){
			showPager = true;
			$("#prev").show();
		}
		if(((this.page - 1) * this.booksPerPage + bookCount) < this.total){
			showPager = true;
			$("#next").show();
		}
		if(showPager){ $("#pager").show(); }

		var pageCount = this.total / this.booksPerPage;
		if(this.total % this.booksPerPage !== 0){ ++pageCount; }
		var pages = $("#pages").html("");
		$.each(range(1, pageCount), function(i, p){
			var li = $("<li></li>");

			if(p === Kininaru.page){
				li.append("<span>"+p+"</span>");
			} else {
				$("<a>"+p+"</a>").attr("href", "javascript:void(0);")
					.bind("click", Kininaru.loadKininaruFn({page: p})).appendTo(li);

				//$("<a>"+p+"</a>").attr("href", "javascript:void(0);").bind("click", function(){
				//	Kininaru.page = p;
				//	Kininaru.loadKininaru();
				//}).appendTo(li);
			}
			pages.append(li);
		});
	};

	Kininaru.showBookFn = function(kininaruObj){
		return function(){
			var screen = $("#screen");
			screen.hide(Kininaru.speed, function(){
				$("#book_title").text(kininaruObj.title);
				$("#book_author").text(kininaruObj.author);
				$("#book_comment").text(kininaruObj.comment);
				$("#book_image").attr("src", kininaruObj.largeimage);
				$("#book_isbn").val(kininaruObj.isbn);

				$.getJSON("/book/user", {isbn: kininaruObj.isbn}, function(res){
					var ul = $("#book_users").html("");
					$.each(res, function(i, v){
						var key = Kininaru.getKey(v);
						var li = $("<li></li>");
						//$("<a></a>").attr("href", "/#!/" + key).bind("click", Kininaru.loadUserKininaruFn(key)
						$("<a></a>").attr("href", "/#!/" + key).bind("click", Kininaru.loadKininaruFn({
							targetURL: "/kininaru/user?key=" + key + "&",
							page: 1,
							showUser: true
						})).append("<img src='" + v.avatar + "' />").appendTo(li);
						ul.append(li);
					});
					screen.show(Kininaru.speed);
				});

			});
		};
	};

	Kininaru.loadKininaruFn = function(opt){
		return function(){
			$("#screen").hide(Kininaru.speed);
			if(isNotBlank(opt.targetURL)){ Kininaru.targetURL = opt.targetURL; }
			if(isNotNull(opt.page)){ Kininaru.page = opt.page; }
			if(isNotNull(opt.showUser)){ Kininaru.showUser = opt.showUser; }
			Kininaru.loadKininaru();
			return false;
		};
	};

	//Kininaru.loadUserKininaruFn = function(key){
	//	return function(){
	//		$("#screen").hide(Kininaru.speed);
	//		Kininaru.targetURL = "/kininaru/user?key=" + key + "&";
	//		Kininaru.page = 1;
	//		Kininaru.showUser = true;
	//		Kininaru.loadKininaru();
	//	};
	//};

	Kininaru.loadKininaru = function(){
		var ul = $("#kininaru").html(Kininaru.loaderImage);

		Kininaru.hidePager();

		$.getJSON(Kininaru.targetURL + "with_total=true&page=" + Kininaru.page, {}, function(res){
			ul.hide(Kininaru.speed, function(){
				ul.html("");

				// total
				Kininaru.total = res.total;

				//ul.setTemplate(Kininaru.template.main).processTemplate({
				//	showUser: Kininaru.showUser,
				//	result: $.map(res.result, function(v){
				//		return(v["_date"] = v.date.split(" ")[0], v);
				//	})
				//});

				// user
				if(Kininaru.showUser && res.result.length > 0){
					var img = $("<img />").attr("src", res.result[0].avatar);
					$("<div></div>").append(img).append("<p>"+ res.result[0].nickname +"さんの気になる本</p>").appendTo(ul);
				}

				var count = 0, lastDate = "";
				//count = res.result.length;

				$.each(res.result, function(i, v){
					var date = v.date.split(" ")[0];
					if(lastDate !== date){
						var tmp = date.split("/");
						ul.append("<li class='date'><p>" + tmp[0] + "<br />" +  tmp[1] + "/" + tmp[2] + "</p></li>");
						lastDate = date;
					}

					//ul.setTemplate(Kininaru.template.kininaru).processTemplate(v);

					var img = $("<img />")
						.attr("src", v.largeimage)
						.attr("title", v.comment)
						.attr("alt", v.title)
						.addClass("thumbnail")
						.bind("click", Kininaru.showBookFn(v));

					var li = $("<li></li>").addClass("book").append(img);

					if(isNotBlank(v.comment)){
						var comment = $("<p>" + v.comment + "</p>").addClass("commentTip");
						li.append(comment);
					}

					ul.append(li);
					++count;
				});

				Kininaru.pager(count);
				ul.show(Kininaru.speed);
			});
		});
	};


	Kininaru.loadRecentKininaru = function(){
		var ul = $("#recent").html(Kininaru.loaderImage);

		$.getJSON("/kininaru/list", {}, function(res){
			ul.html("");

			$.each(res, function(i, v){
				var key = Kininaru.getKey(v);
				var img = $("<img src='"+ v.largeimage +"' />").addClass("thumbnail").bind("click", Kininaru.showBookFn(v));
				//var avatar = $("<img />").attr("src", v.avatar).addClass("user").bind("click", Kininaru.loadUserKininaruFn(key));
				var avatar = $("<img />").attr("src", v.avatar).addClass("user").bind("click", Kininaru.loadKininaruFn({
					targetURL: "/kininaru/user?key=" + key + "&",
					page: 1,
					showUser: true
				}));
				var li = $("<li></li>").addClass("book").append(avatar).append(img).appendTo(ul);
			});
			ul.show(Kininaru.speed);
		});
	};

	$(function(){
		$.getJSON("/message", {}, function(res){
			if(isNotBlank(res)){
				var msg = $("#message");
				msg.text(res);
				msg.show(Kininaru.speed, function(){
					var t = setTimeout(function(){
						msg.hide(Kininaru.speed, function(){
							clearTimeout(t);
						});
					}, 5000);
				});
			}
		});

		$("#prev").bind("click", Kininaru.loadKininaruFn({page: Kininaru.page - 1}));
		$("#next").bind("click", Kininaru.loadKininaruFn({page: Kininaru.page + 1}));

		//$("#prev").bind("click", function(){
		//	--Kininaru.page;
		//	Kininaru.loadKininaru();
		//	return false;
		//});
		//$("#next").bind("click", function(){
		//	++Kininaru.page;
		//	Kininaru.loadKininaru();
		//	return false;
		//});

		$("#screen_close > a").bind("click", function(){
			$("#screen").hide(Kininaru.speed);
		});

	});
}(this));
