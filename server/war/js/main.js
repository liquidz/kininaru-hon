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

	var loadMessage = function(){
		$.getJSON("/message", {}, function(res){
			if(isNotBlank(res)){
				var msg = $("#message");
				msg.text(res);
				msg.show(Kininaru.speed, function(){
					var t = setTimeout(function(){
						msg.hide(Kininaru.speed, function(){ clearTimeout(t); });
					}, 5000);
				});
			}
		});
	};

	var compileTemplates = function(){
		$("script[type='text/x-jquery-tmpl']").each(function(){
			var _tmpl = $(this);
			var name = _tmpl.data("name");
			if(isNotBlank(name)){ _tmpl.template(name); }
		});
	};

	Kininaru.getKey = function(obj){ return((obj.key) ? obj.key : obj.userkey); };

	Kininaru.pager = function(bookCount){
		if(this.page > 1){ $("#prev").show(); }
		if(((this.page - 1) * this.booksPerPage + bookCount) < this.total){ $("#next").show(); }

		var pageCount = this.total / this.booksPerPage;
		if(this.total % this.booksPerPage !== 0){ ++pageCount; }
		var pages = $("#pages").html("");
		$.each(range(1, pageCount), function(i, v){
			console.log(v);
			$.tmpl("_pager_", {page: v, nowPage: Kininaru.page}).appendTo(pages);
		});
		$("#pages a").bind("click", function(ev){
			Kininaru.loadKininaruFn({ page: parseInt($(ev.target).data("page")) })();
		});
	};

	Kininaru.showBookFn = function(kininaruObj){
		return function(){
			var screen = $("#screen");
			screen.hide(Kininaru.speed, function(){
				var screenContent = $("#screenContent");
				screenContent.html("");
				$.tmpl("_screen_", kininaruObj).appendTo(screenContent);

				$.getJSON("/book/user", {isbn: kininaruObj.isbn}, function(res){
					var ul = $("#book_users").html("");

					$.each(res, function(i, v){
						var key = Kininaru.getKey(v);
						v["key"] = key;
						$.tmpl("_user_list_", v)
							.bind("click", Kininaru.loadKininaruFn({
								targetURL: "/kininaru/user?key=" + key + "&",
								page: 1,
								showUser: true
							})).appendTo(ul);
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
					$.tmpl("_user_", res.result[0]).appendTo(ul);
				}

				var lastDate = "";
				$.each(res.result, function(i, v){
					var date = v.date.split(" ")[0];
					if(lastDate !== date){
						$.tmpl("_date_list_", {date: date.split("/")}).appendTo(ul);
						lastDate = date;
					}

					$.tmpl("_thumb_list_", v)
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
				var img = $.tmpl("_thumb_", v).bind("click", Kininaru.showBookFn(v));
				var avatar = $.tmpl("_avatar_", v).bind("click", Kininaru.loadKininaruFn({
					targetURL: "/kininaru/user?key=" + key + "&",
					page: 1,
					showUser: true
				}));

				$.tmpl("_book_").append(avatar).append(img).appendTo(ul);
			});
			ul.show(Kininaru.speed);
		});
	};

	$(function(){
		loadMessage();

		$("#prev").bind("click", Kininaru.loadKininaruFn({page: Kininaru.page - 1}));
		$("#next").bind("click", Kininaru.loadKininaruFn({page: Kininaru.page + 1}));

		$("#screen_close > a").bind("click", function(){
			$("#screen").hide(Kininaru.speed);
		});

		compileTemplates();
	});
}(this));
