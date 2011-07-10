$(function(){
	var ua = navigator.userAgent;

	var loadCSS = function(href){
		var head = document.getElementsByTagName("head")[0];
		var css = document.createElement("link");
		css.setAttribute("rel", "stylesheet");
		css.setAttribute("type", "text/css");
		css.setAttribute("href", href);
		head.appendChild(css);
	};

	if(ua.indexOf("Android") !== -1){
		loadCSS("/css/smartphone.css");
	} else {
		loadCSS("/css/main.css");
	}
});
