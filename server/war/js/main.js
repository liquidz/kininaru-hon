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

	Kininaru.loadKininaru = function(){
		var ul = $("#kininaru").html(Kininaru.loaderImage);

		Kininaru.hidePager();

		$.getJSON(Kininaru.targetURL + "with_total=true&page=" + Kininaru.page, {}, function(res){
			ul.hide(Kininaru.speed, function(){
				ul.html("");

				// total
				Kininaru.total = res.total;

				// user
				if(Kininaru.showUser && res.result.length > 0){
					$.tmpl("_k_user_", res.result[0]).appendTo(ul);
				}

				var lastDate = "";
				$.each(res.result, function(i, v){
					var date = v.date.split(" ")[0];
					if(lastDate !== date){
						$.tmpl("_k_date_", {date: date.split("/")}).appendTo(ul);
						lastDate = date;
					}

					$.tmpl("_k_thumb_", v)
						.bind("click", Kininaru.showBookFn(v))
						.appendTo(ul);
				});

				Kininaru.pager(res.result.length);
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
				var img = $("#thumbTemplate").tmpl(v).bind("click", Kininaru.showBookFn(v));
				var avatar = $("#avatarTemplate").tmpl(v).bind("click", Kininaru.loadKininaruFn({
					targetURL: "/kininaru/user?key=" + key + "&",
					page: 1,
					showUser: true
				}));

				$("#bookListWrapper").tmpl().append(avatar).append(img).appendTo(ul);
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


		$("#screen_close > a").bind("click", function(){
			$("#screen").hide(Kininaru.speed);
		});

		$("#userTemplate").template("_k_user_");
		$("#dateListTemplate").template("_k_date_");
		$("#thumbListTemplate").template("_k_thumb_");

	});
}(this));
