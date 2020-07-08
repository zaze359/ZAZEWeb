function InitLeftMenu() {
	$("#acc").empty();
	var menulist = "";
	menulist += '<div class="easyui-accordion" data-options="fit:true,border:false">';
	$.each(_menus.menus, function(i, n) {
		menulist += '<div title="' + n.menuname + '" icon="' + n.icon
				+ '" style="overflow:auto;border:0px">';
		menulist += '<ul>';
		$.each(n.menus, function(j, o) {
			menulist += '<li><div><a rel="' + o.url + '" ref="' + o.menuid
					+ '" href="javascript:void(0);"><span class="icon ' + o.icon
					+ '" >&nbsp;</span><span class="nav">' + o.menuname
					+ '</span></a></div></li>';
		});
		menulist += '</ul></div>';
	});
	menulist += '</div>';
	$("#acc").append(menulist);
	$('#acc li a').click(function() {
		var tabTitle = $(this).children('.nav').text();
		var url = $(this).attr("rel");
		var menuid = $(this).attr("ref");
		var icon = getIcon(menuid);

		addTab(tabTitle, url, icon);
		$('#acc li div').removeClass("selected");
		$(this).parent().addClass("selected");
	}).hover(function() {
		$(this).parent().addClass("hover");
	}, function() {
		$(this).parent().removeClass("hover");
	});

	// 获取左侧导航的图标
	function getIcon(menuid) {
		var icon = 'icon ';
		$.each(_menus.menus, function(i, n) {
			$.each(n.menus, function(j, o) {
				if (o.menuid == menuid) {
					icon += o.icon;
				}
			});
		});
		return icon;
	}

	// 导航菜单绑定初始化
	// $(".easyui-accordion").accordion();
}

// 不存在相应tabs就添加 存在就选中
function addTab(subtitle, url, icon) {
	if (!$('#tabs').tabs('exists', subtitle)) {
		$('#tabs').tabs('add', {
			title : subtitle,
			content : createFrame(url),
			closable : true,
			icon : icon,
		});
	} else {
		$('#tabs').tabs('select', subtitle);
	}
	//
	tabClose();
}
function createFrame(url) {
	var s = '<iframe scrolling="auto" frameborder="0"  src="' + url
			+ '" style="width:100%;height:100%;"></iframe>';
	return s;
}
/*
function createFrame(url) {
	var s = '<div><jsp:include page="' + url
			+ '"></jsp:include></div>';
	return s;
}
 */
function tabClose() {
	/* 双击关闭TAB选项卡 */
	$(".tabs-inner").dblclick(function() {
		var subtitle = $(this).children(".tabs-closable").text();
		$('#tabs').tabs('close', subtitle);
	});
	/* 为选项卡绑定右键 */
	$(".tabs-inner").bind('contextmenu', function(e) {
		$('#mm').menu('show', {
			left : e.pageX,
			top : e.pageY
		});
		var subtitle = $(this).children(".tabs-closable").text();
		$('#mm').data("currtab", subtitle);
		$('#tabs').tabs('select', subtitle);
		return false;
	});
}

// 绑定右键菜单事件
function tabCloseEven() {
	// 关闭当前
	$('#mm-tabclose').click(function() {
		var currtab_title = $('#mm').data("currtab");
		$('#tabs').tabs('close', currtab_title);
	});
	// 全部关闭
	$('#mm-tabcloseall').click(function() {
		$('.tabs-inner span').each(function(i, n) {
			var t = $(n).text();
			$('#tabs').tabs('close', t);
		});
	});
	// 关闭除当前之外的TAB
	$('#mm-tabcloseother').click(function() {
		var currtab_title = $('#mm').data("currtab");
		$('.tabs-inner span').each(function(i, n) {
			var t = $(n).text();
			if (t != currtab_title)
				$('#tabs').tabs('close', t);
		});
	});
	// 关闭当前右侧的TAB
	$('#mm-tabcloseright').click(function() {
		var nextall = $('.tabs-selected').nextAll();
		if (nextall.length == 0) {
			// msgShow('系统提示','后边没有啦~~','error');
			alert('后边没有啦~~');
			return false;
		}
		nextall.each(function(i, n) {
			var t = $('a:eq(0) span', $(n)).text();
			$('#tabs').tabs('close', t);
		});
		return false;
	});
	// 关闭当前左侧的TAB
	$('#mm-tabcloseleft').click(function() {
		var prevall = $('.tabs-selected').prevAll();
		if (prevall.length == 0) {
			// alert('到头了，前边没有啦~~');
			return false;
		}
		prevall.each(function(i, n) {
			var t = $('a:eq(0) span', $(n)).text();
			$('#tabs').tabs('close', t);
		});
		return false;
	});

	// 退出
	$("#mm-exit").click(function() {
		$('#mm').menu('hide');
	});
}
