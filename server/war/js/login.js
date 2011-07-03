$(function(){
	var loaderImage = "<img src='/img/ajax-loader.gif' alt='読み込み中' />";
	var loginPart = $("#login_part");

	console.log("hash = " + location.hash)

	loginPart.html(loaderImage);
	$.getJSON("/check/login", {}, function(res){
		loginPart.html("");

		if(res.loggedin){
			loginPart.append("<a href='"+res.url+"'>ログアウト</a>");
			$("#avatar").append("<img src='" + res.avatar + "' />").append("<p>"+ res.nickname +"</p>");

			$(".when_login").show();
			$(".when_not_login").hide();

			Kininaru.loadKininaru();

		} else {
			if(location.pathname === "/"){
				loginPart.append("<a href='"+res.url+"'>ログイン</a>");

				Kininaru.loadRecentKininaru();

			} else {
				location.href = "/";
			}
		}
	});
});
