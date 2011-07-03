(function(window, undefined){

	if(!window.Kininaru){
		Kininaru = {};
	}

	Kininaru.loaderImage = "<img src='/img/ajax-loader.gif' />";

	Kininaru.total = -1;
	Kininaru.page = 1;
	Kininaru.booksPerPage = 10;
	//Kininaru.showingISBN = null;

	Kininaru.speed = 500;

	Kininaru.targetURL = "/kininaru/my?";
	Kininaru.showUser = false;

	Kininaru.hidePager = function(){
		$("#pager").hide();
		$("#prev").hide();
		$("#next").hide();
	};

	Kininaru.util = {};
	Kininaru.util.range = function(start, end){
		var res = [];
		for(var i = start; i < end; ++i){
			res.push(i);
		}
		return res;
	};

	Kininaru.getKey = function(obj){
		return((obj.key) ? obj.key : obj.userkey);
	};

	Kininaru.pager = function(bookCount){
		var showPager = 0;
		if(this.page > 1){
			++showPager;
			$("#prev").show();
		}
		if(((this.page - 1) * this.booksPerPage + bookCount) < this.total){
			++showPager;
			$("#next").show();
		}
		if(showPager > 0){
			$("#pager").show();
		}

		var x = this.total / this.booksPerPage;
		if(this.total % this.booksPerPage !== 0){ ++x; }
		var pages = $("#pages").html("");

		$.each(Kininaru.util.range(1, x), function(i, p){
			var li = $("<li></li>");

			if(p === Kininaru.page){
				li.append("<span>"+p+"</span>");
			} else {
				$("<a>"+p+"</a>").attr("href", "javascript:void(0);").bind("click", function(){
					Kininaru.page = p;
					Kininaru.loadKininaru();
				}).appendTo(li);
			}
			pages.append(li);
		});
	};

	Kininaru.isNear = function(pos, x, y){ // {{{
		for(var i = 0, l = pos.length; i < l; ++i){
			if(Math.abs(pos[i][0] - x) < 75
					&& Math.abs(pos[i][1] - y) < 55){
				return true;
			}
		}
		return false;
	}; // }}}

	Kininaru.showBookFn = function(kininaruObj){
		return function(){
			var screen = $("#screen");
			screen.hide(Kininaru.speed, function(){
				$("#book_title").text(kininaruObj.title);
				$("#book_author").text(kininaruObj.author);
				$("#book_comment").text(kininaruObj.comment);
				$("#book_image").attr("src", kininaruObj.largeimage);

				$.getJSON("/book/user", {isbn: kininaruObj.isbn}, function(res){
					var ul = $("#book_users").html("");
					$.each(res, function(i, v){
						var key = Kininaru.getKey(v);
						$("<a></a>").attr("href", "/#!/" + key).bind("click", Kininaru.loadUserKininaruFn(key)
						//	function(){
						//	$("#screen").hide(Kininaru.speed);
						//	Kininaru.targetURL = "/kininaru/user?key=" + v.key + "&";
						//	Kininaru.page = 1;
						//	Kininaru.showUser = true;
						//	Kininaru.loadKininaru();
						//}
						).append("<img src='" + v.avatar + "' />").appendTo(ul);
					});
					screen.show(Kininaru.speed);
				});

			});
		};
	};

	Kininaru.loadUserKininaruFn = function(key){
		return function(){
			$("#screen").hide(Kininaru.speed);
			Kininaru.targetURL = "/kininaru/user?key=" + key + "&";
			Kininaru.page = 1;
			Kininaru.showUser = true;
			Kininaru.loadKininaru();
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
					var img = $("<img />").attr("src", res.result[0].avatar);
					$("<div></div>").append(img).append("<p>"+ res.result[0].nickname +"さんの気になる本</p>").appendTo(ul);
				}

				var count = 0, lastDate = "";
				$.each(res.result, function(i, v){
					var date = v.date.split(" ")[0];
					if(lastDate !== date){
						var tmp = date.split("/");
						ul.append("<li class='date'><p>" + tmp[0] + "<br />" +  tmp[1] + "/" + tmp[2] + "</p></li>");
						lastDate = date;
					}

					var img = $("<img src='"+ v.largeimage +"' />").addClass("thumbnail").bind("click", Kininaru.showBookFn(v));
					var li = $("<li></li>").addClass("book").append(img).appendTo(ul);

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

			// user
			//if(Kininaru.showUser && res.result.length > 0){
			//	var img = $("<img />").attr("src", res.result[0].avatar);
			//	$("<div></div>").append(img).append("<p>"+ res.result[0].nickname +"さんの気になる本</p>").appendTo(ul);
			//}

			$.each(res, function(i, v){
				var key = Kininaru.getKey(v);
				var img = $("<img src='"+ v.largeimage +"' />").addClass("thumbnail").bind("click", Kininaru.showBookFn(v));
				var avatar = $("<img />").attr("src", v.avatar).addClass("user").bind("click", Kininaru.loadUserKininaruFn(key));
				var li = $("<li></li>").addClass("book").append(avatar).append(img).appendTo(ul);
			});
			ul.show(Kininaru.speed);
		});
	};


	$(function(){
		$.getJSON("/message", {}, function(res){
			if(res !== ""){
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


		$("#prev").bind("click", function(){
			--Kininaru.page;
			Kininaru.loadKininaru();
			return false;
		});
		$("#next").bind("click", function(){
			++Kininaru.page;
			Kininaru.loadKininaru();
			return false;
		});

		$("#screen_close > a").bind("click", function(){
			$("#screen").hide(Kininaru.speed);
		});

	});
}(this));
