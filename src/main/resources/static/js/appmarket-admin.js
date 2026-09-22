/* 应用市场 · 管理后台脚本（jQuery + Bootstrap）
 * 提供：应用增删改、版本与下载源管理、触发自动采集。
 * 所有接口统一返回 {code,data,msg} 信封。
 */
(function ($) {
    var API = (window.ctx || '') + '/api/v1/appmarket/admin';

    var state = {
        apps: [],
        keyword: '',
        currentAppId: null,
        currentVersionId: null,
        currentDetail: null
    };

    // ------------------------------------------------------------ utils
    function esc(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // 后台表格缩略图加载失败时替换为默认占位图标（供 onerror 内联调用，需挂到 window）
    function appIconFallbackSm(img) {
        var ph = document.createElement('span');
        ph.className = 'app-icon-sm app-icon-placeholder';
        ph.innerHTML = '<i class="fa fa-cube" aria-hidden="true"></i>';
        if (img && img.parentNode) img.parentNode.replaceChild(ph, img);
    }
    window.appIconFallbackSm = appIconFallbackSm;

    function fmtDate(ms) {
        try {
            var d = new Date(ms);
            return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
        } catch (e) {
            return '';
        }
    }

    function notify(type, msg) {
        var cls = type === 'error' ? 'alert-danger' : (type === 'warn' ? 'alert-warning' : 'alert-success');
        $('#alertBox').html('<div class="alert ' + cls + ' py-2 mb-3">' + esc(msg) + '</div>');
        setTimeout(function () { $('#alertBox').empty(); }, 4000);
    }

    function ajax(method, url, data, timeout) {
        return $.ajax({
            url: url,
            type: method,
            contentType: 'application/json; charset=utf-8',
            data: data != null ? JSON.stringify(data) : undefined,
            dataType: 'json',
            timeout: timeout   // 单位 ms；不传则无超时限制（长导入任务用）
        });
    }

    function errMsg(xhr, fallback) {
        var m = xhr && xhr.responseJSON && xhr.responseJSON.msg;
        return m || fallback;
    }

    // ------------------------------------------------------------ app list
    function loadApps() {
        $.getJSON(API + '/apps').done(function (res) {
            state.apps = (res && res.data) || [];
            renderApps();
        }).fail(function () {
            $('#appTbody').html('<tr><td colspan="7" class="text-center text-danger py-4">加载失败</td></tr>');
        });
    }

    function renderApps() {
        var apps = state.apps;
        if (state.keyword) {
            var kw = state.keyword.toLowerCase();
            apps = apps.filter(function (a) {
                return [a.name, a.packageName, a.developer, a.category]
                    .some(function (f) { return f && String(f).toLowerCase().indexOf(kw) >= 0; });
            });
        }
        if (!apps.length) {
            $('#appTbody').html('<tr><td colspan="7" class="text-center text-muted py-4">' +
                (state.keyword ? '没有匹配「' + esc(state.keyword) + '」的已导入应用。' : '暂无应用，点击右上角「新增应用」或「触发自动采集」。') +
                '</td></tr>');
            $('#adminSearchCount').text(state.keyword ? '0 条' : '');
            return;
        }
        $('#adminSearchCount').text(state.keyword ? ('匹配 ' + apps.length + ' / ' + state.apps.length + ' 条') : '');
        var html = apps.map(function (app) {
            var icon = app.iconUrl
                ? '<img class="app-icon-sm" src="' + esc(app.iconUrl) + '" alt="" onerror="appIconFallbackSm(this)"/>'
                : '<span class="app-icon-sm app-icon-placeholder"><i class="fa fa-cube" aria-hidden="true"></i></span>';
            return '' +
                '<tr>' +
                '  <td>' + app.id + '</td>' +
                '  <td>' + icon + ' <a href="' + (window.ctx || '') + '/appmarket/' + app.id + '" target="_blank" rel="noopener">' + esc(app.name) + '</a></td>' +
                '  <td><code>' + esc(app.packageName || '') + '</code></td>' +
                '  <td>' + esc(app.category || '') + '</td>' +
                '  <td>' + esc(app.developer || '') + '</td>' +
                '  <td>' + (app.versionCount || 0) + '</td>' +
                '  <td>' +
                '    <button class="btn btn-outline-primary btn-sm mr-1" onclick="Admin.openManage(' + app.id + ')">版本/下载源</button>' +
                '    <button class="btn btn-outline-secondary btn-sm mr-1" onclick="Admin.openAppModal(' + app.id + ')">编辑</button>' +
                '    <button class="btn btn-outline-danger btn-sm" onclick="Admin.deleteApp(' + app.id + ')">删除</button>' +
                '  </td>' +
                '</tr>';
        }).join('');
        $('#appTbody').html(html);
    }

    // ------------------------------------------------------------ app crud
    function openAppModal(id) {
        var app = (id != null) ? findApp(id) : null;
        $('#appId').val(app ? app.id : '');
        $('#appName').val(app ? (app.name || '') : '');
        $('#appPackageName').val(app ? (app.packageName || '') : '');
        $('#appCategory').val(app ? (app.category || '') : '');
        $('#appDeveloper').val(app ? (app.developer || '') : '');
        $('#appIconUrl').val(app ? (app.iconUrl || '') : '');
        $('#appOfficialUrl').val(app ? (app.officialUrl || '') : '');
        $('#appSummary').val(app ? (app.summary || '') : '');
        $('#appModalTitle').text(app ? '编辑应用' : '新增应用');
        $('#appModal').modal('show');
    }

    function saveApp() {
        var id = $('#appId').val();
        var form = {
            name: $('#appName').val().trim(),
            packageName: $('#appPackageName').val().trim(),
            category: $('#appCategory').val().trim(),
            developer: $('#appDeveloper').val().trim(),
            iconUrl: $('#appIconUrl').val().trim(),
            officialUrl: $('#appOfficialUrl').val().trim(),
            summary: $('#appSummary').val().trim()
        };
        if (!form.name) { notify('warn', '应用名称必填'); return; }
        var req = id ? ajax('PUT', API + '/apps/' + id, form) : ajax('POST', API + '/apps', form);
        req.done(function () {
            $('#appModal').modal('hide');
            notify('ok', '保存成功');
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '保存失败'));
        });
    }

    function deleteApp(id) {
        var app = findApp(id);
        if (!confirm('确定删除应用「' + (app ? app.name : '#' + id) + '」吗？\n其下所有版本与下载源将一并删除，且不可恢复。')) return;
        ajax('DELETE', API + '/apps/' + id).done(function () {
            notify('ok', '已删除');
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '删除失败'));
        });
    }

    // ------------------------------------------------------------ version & source
    function openManage(id) {
        state.currentAppId = id;
        var app = findApp(id);
        $('#manageAppName').text(app ? app.name : ('#' + id));
        $('#manageModal').modal('show');
        loadManage(id);
    }

    function loadManage(id) {
        $('#versionList').html('<div class="text-muted small">加载中…</div>');
        $.getJSON(API + '/apps/' + id).done(function (res) {
            var app = res && res.data;
            state.currentDetail = app;
            if (!app) { $('#versionList').html('<div class="text-danger small">未找到应用</div>'); return; }
            var versions = app.versions || [];
            if (!versions.length) {
                $('#versionList').html('<div class="text-muted small">暂无版本，点击右上角「新增版本」。</div>');
                return;
            }
            $('#versionList').html(versions.map(renderVersion).join(''));
        }).fail(function () {
            $('#versionList').html('<div class="text-danger small">加载失败</div>');
        });
    }

    function renderVersion(v) {
        var sources = (v.sources || []).map(function (s) {
            return '<div class="d-flex align-items-center mb-1">' +
                '<a href="' + esc(s.downloadUrl) + '" target="_blank" rel="noopener" class="mr-2">' +
                esc(s.sourceName || s.sourceType || '下载') + '</a>' +
                '<span class="badge badge-secondary mr-2">' + esc(s.sourceType || '') + '</span>' +
                (s.region ? '<span class="text-muted small mr-2">' + esc(s.region) + '</span>' : '') +
                '<button class="btn btn-link btn-sm text-danger p-0" onclick="Admin.deleteSource(' + s.id + ')">删除</button>' +
                '</div>';
        }).join('') || '<div class="text-muted small">暂无下载源</div>';

        return '<div class="border rounded p-2 mb-2">' +
            '<div class="d-flex justify-content-between align-items-center">' +
            '  <div><strong>v' + esc(v.versionName || '') + '</strong> ' +
            '    <span class="text-muted small">' + (v.releaseDate ? fmtDate(v.releaseDate) : '') +
            (v.sizeMb ? ' · ' + v.sizeMb + ' MB' : '') + ' · ' + (v.sourceCount || 0) + ' 来源</span></div>' +
            '  <div>' +
            '    <button class="btn btn-outline-success btn-sm mr-1" onclick="Admin.openSourceModal(' + v.id + ')"><i class="fa fa-plus"></i> 下载源</button>' +
            '    <button class="btn btn-outline-danger btn-sm" onclick="Admin.deleteVersion(' + v.id + ')"><i class="fa fa-trash"></i></button>' +
            '  </div>' +
            '</div>' +
            (v.changelog ? '<div class="text-muted small mt-1">' + esc(v.changelog) + '</div>' : '') +
            '<div class="mt-2">' + sources + '</div>' +
            '</div>';
    }

    function openVersionModal() {
        $('#verName').val('');
        $('#verCode').val('');
        $('#verDate').val('');
        $('#verSize').val('');
        $('#verChangelog').val('');
        $('#versionModal').modal('show');
    }

    function saveVersion() {
        var form = {
            versionName: $('#verName').val().trim(),
            versionCode: numOrNull('#verCode'),
            releaseDate: $('#verDate').val().trim(),
            sizeMb: numOrNull('#verSize'),
            changelog: $('#verChangelog').val().trim()
        };
        if (!form.versionName) { notify('warn', '版本名必填'); return; }
        ajax('POST', API + '/apps/' + state.currentAppId + '/versions', form).done(function () {
            $('#versionModal').modal('hide');
            notify('ok', '已新增版本');
            loadManage(state.currentAppId);
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '新增版本失败'));
        });
    }

    function deleteVersion(id) {
        if (!confirm('确定删除该版本吗？其下所有下载源将一并删除。')) return;
        ajax('DELETE', API + '/versions/' + id).done(function () {
            notify('ok', '已删除版本');
            loadManage(state.currentAppId);
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '删除失败'));
        });
    }

    function openSourceModal(versionId) {
        state.currentVersionId = versionId;
        var name = '';
        var detail = state.currentDetail;
        if (detail && detail.versions) {
            var v = detail.versions.filter(function (x) { return x.id === versionId; })[0];
            if (v) name = v.versionName || '';
        }
        $('#sourceVersionName').text('v' + name);
        $('#srcName').val('');
        $('#srcType').val('GITHUB');
        $('#srcUrl').val('');
        $('#srcRegion').val('');
        $('#srcNote').val('');
        $('#sourceModal').modal('show');
    }

    function saveSource() {
        var form = {
            sourceName: $('#srcName').val().trim(),
            sourceType: $('#srcType').val(),
            downloadUrl: $('#srcUrl').val().trim(),
            region: $('#srcRegion').val().trim(),
            note: $('#srcNote').val().trim()
        };
        if (!form.downloadUrl) { notify('warn', '下载地址必填'); return; }
        ajax('POST', API + '/versions/' + state.currentVersionId + '/sources', form).done(function () {
            $('#sourceModal').modal('hide');
            notify('ok', '已新增下载源');
            loadManage(state.currentAppId);
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '新增下载源失败'));
        });
    }

    function deleteSource(id) {
        if (!confirm('确定删除该下载源吗？')) return;
        ajax('DELETE', API + '/sources/' + id).done(function () {
            notify('ok', '已删除下载源');
            loadManage(state.currentAppId);
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '删除失败'));
        });
    }

    // ------------------------------------------------------------ collect
    function collect() {
        var $btn = $('#btnCollect');
        $btn.prop('disabled', true).html('<i class="fa fa-spinner fa-spin"></i> 采集中…');
        ajax('POST', API + '/collect').done(function (res) {
            var d = (res && res.data) || {};
            var html = '<p class="mb-2">共 <b>' + (d.targets || 0) + '</b> 个采集目标：' +
                '新增应用 <b>' + (d.appsCreated || 0) + '</b>，' +
                '版本 <b>' + (d.versionsAdded || 0) + '</b>，' +
                '下载源 <b>' + (d.sourcesAdded || 0) + '</b>。</p>';
            html += '<ul class="pl-3 mb-0 small text-muted">' +
                (d.messages || []).map(function (m) { return '<li>' + esc(m) + '</li>'; }).join('') +
                '</ul>';
            $('#collectBody').html(html);
            $('#collectModal').modal('show');
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '采集失败'));
        }).always(function () {
            $btn.prop('disabled', false).html('<i class="fa fa-cloud-download-alt"></i> 触发自动采集');
        });
    }

    // ------------------------------------------------------------ batch complete from 应用宝
    function batchCompleteMyapp() {
        var $btn = $('#btnBatchComplete');
        $btn.prop('disabled', true).html('<i class="fa fa-spinner fa-spin"></i> 启动中…');
        // 旧实现同步等待最长 120s、期间无任何进度；改为启动后台任务 + SSE 实时滚动态
        // （逐个应用「补链接 → 抓元数据」的结果会实时出现在链路面板里）
        startTracedImport('batch-complete');
        // 任务已在后台执行（幂等，可重复点击），按钮立即恢复
        $btn.prop('disabled', false).html('<i class="fa fa-magic"></i> 批量补全应用宝元数据');
    }

    // ------------------------------------------------------------ helpers
    function findApp(id) {
        return state.apps.filter(function (a) { return a.id === id; })[0];
    }

    function numOrNull(sel) {
        var v = $(sel).val();
        if (v === '' || v == null) return null;
        var n = Number(v);
        return isNaN(n) ? null : n;
    }

    $(function () {
        loadApps();
        bindAdminSearch();
        $('#apkFile').on('change', onApkSelected);
    });

    // ------------------------------------------------------------ search ①：当前已导入应用列表过滤
    function bindAdminSearch() {
        var t = null;
        $('#adminSearchInput').on('input', function () {
            var v = $(this).val();
            clearTimeout(t);
            t = setTimeout(function () {
                state.keyword = (v || '').trim();
                renderApps();
            }, 250);
        });
    }

    // ------------------------------------------------------------ search ②：外部资源查询与导入
    function openExternalModal() {
        $('#extInput').val('');
        $('#extResult').empty();
        loadExternalSources();
        $('#externalModal').modal('show');
    }

    /** 列出所有已配置的搜索上游（F-Droid / IzzyOnDroid 等），方便查看当前有哪些源 */
    function loadExternalSources() {
        $.getJSON(API + '/external-sources').done(function (res) {
            var list = (res && res.data) || [];
            if (!list.length) {
                $('#extSources').html('<span class="text-muted">暂无已启用的搜索源</span>');
                return;
            }
            var html = '<span class="text-muted mr-2">搜索源：</span>' + list.map(function (s) {
                var cls = s.enabled ? 'badge-success' : 'badge-secondary';
                var title = esc(s.searchUrl || '');
                return '<span class="badge ' + cls + ' mr-1" title="' + title + '">' +
                    esc(s.name || s.id) + (s.enabled ? '' : '（已停用）') + '</span>';
            }).join('');
            $('#extSources').html(html);
        }).fail(function () {
            $('#extSources').html('<span class="text-danger">加载搜索源失败</span>');
        });
    }

    function lookupExternal() {
        var raw = $('#extInput').val().trim();
        if (!raw) { notify('warn', '请输入包名、商店链接或应用名'); return; }
        var $btn = $('#btnExtLookup');
        $btn.prop('disabled', true).html('<i class="fa fa-spinner fa-spin"></i> 查询中…');
        $('#extResult').html('<div class="text-muted small">查询中…</div>');
        // 包名/链接走精确查询；自由文本（应用名）走模糊搜索
        var byPackage = isPackageOrUrl(raw);
        var endpoint = byPackage ? '/external-lookup' : '/external-search';
        // 只读查询。各上游现在「各自计时、各自超时」（最慢的 Izzy 索引可达 15s），
        // 因此整体等待放宽到 30s，避免前端先超时、把后端已查到的结果丢掉。
        $.ajax({
            url: API + endpoint,
            type: 'GET',
            data: { packageName: raw, keyword: raw },
            dataType: 'json',
            timeout: 30000
        }).done(function (res) {
            if (byPackage) renderLookupPreview(res);
            else renderSearchList(res);
        }).fail(function (xhr, textStatus) {
            var msg = (textStatus === 'timeout')
                ? '查询超时（上游响应过慢），请稍后重试'
                : errMsg(xhr, '查询失败');
            $('#extResult').html('<div class="alert alert-danger py-2 mb-0">' + esc(msg) + '</div>');
        }).always(function () {
            $btn.prop('disabled', false).html('<i class="fa fa-search"></i> 查询');
        });
    }

    /**
     * 逐源探测明细：每个上游**单独一行**（状态 / 源名 / 该源自身耗时 / 说明）。
     * 与「预览 / 候选列表」分开渲染，不混在一起——便于一眼看出是哪个源命中、哪个源慢或超时。
     */
    /** 分组标题：国内源 / 国外备选源（后端按可达性分组，国外源仅在国内源未命中时才查询） */
    var GROUP_META = {
        DOMESTIC: { title: '国内源', hint: '常态可达，默认查询', badge: 'badge-primary' },
        OVERSEAS: { title: '国外备选源', hint: '国内源未命中时才降级查询', badge: 'badge-secondary' }
    };

    function groupKeyOf(g) {
        return (g === 'OVERSEAS') ? 'OVERSEAS' : 'DOMESTIC';
    }

    function probeRows(list) {
        return list.map(function (pr) {
            var cls = pr.status === 'HIT' ? 'text-success'
                : (pr.status === 'MISS' ? 'text-muted'
                    : (pr.status === 'TIMEOUT' ? 'text-warning' : 'text-danger'));
            var icon = pr.status === 'HIT' ? 'fa-check-circle'
                : (pr.status === 'MISS' ? 'fa-circle-o'
                    : (pr.status === 'TIMEOUT' ? 'fa-clock-o' : 'fa-exclamation-circle'));
            var label = pr.status === 'HIT' ? '命中'
                : (pr.status === 'MISS' ? '未命中'
                    : (pr.status === 'TIMEOUT' ? '超时' : '异常'));
            return '<div class="d-flex align-items-start py-1 px-2 border-bottom">' +
                '<span class="mr-2 ' + cls + '" style="flex:0 0 4.8rem"><i class="fa ' + icon + '"></i> ' + label + '</span>' +
                '<span class="font-weight-bold mr-2" style="flex:0 0 6.5rem">' + esc(pr.source) + '</span>' +
                '<span class="mr-2 text-muted" style="flex:0 0 4.6rem">' + (pr.elapsedMs != null ? pr.elapsedMs + 'ms' : '-') + '</span>' +
                '<span class="small text-muted" style="min-width:0;word-break:break-all">' +
                esc(pr.message || pr.url || '') + '</span>' +
                '</div>';
        }).join('');
    }

    function renderProbes(probes, overseasQueried) {
        var list = probes || [];
        if (!list.length) return '';
        var groups = ['DOMESTIC', 'OVERSEAS'].map(function (g) {
            return { key: g, items: list.filter(function (p) { return groupKeyOf(p.group) === g; }) };
        }).filter(function (x) { return x.items.length; });

        var blocks = groups.map(function (x) {
            var meta = GROUP_META[x.key];
            var note = (x.key === 'OVERSEAS' && overseasQueried === false)
                ? ' <span class="text-muted small">（本次国内源已命中，未查询）</span>' : '';
            return '<div class="mb-2">' +
                '<div class="small font-weight-bold mb-1">' +
                '<span class="badge ' + meta.badge + '">' + meta.title + '</span> ' +
                '<span class="text-muted">' + meta.hint + '</span>' + note + '</div>' +
                '<div class="border rounded" style="max-height:220px;overflow-y:auto">' + probeRows(x.items) + '</div>' +
                '</div>';
        }).join('');

        return '<div class="mt-3">' +
            '<div class="small font-weight-bold mb-1">各上游探测明细（每源单独计时，互不影响）</div>' +
            blocks + '</div>';
    }

    function renderLookupPreview(res) {
        var data = (res && res.data) || {};
        var p = data.preview;
        if (!p) {
            $('#extResult').html('<div class="alert alert-warning py-2 mb-0">' +
                esc((res && res.msg) || '未找到该外部应用') + '</div>' + renderProbes(data.probes, data.overseasQueried));
            return;
        }
        var icon = p.iconSrc
            ? '<img class="app-icon-sm" src="' + esc(p.iconSrc) + '" alt=""/>'
            : '<span class="app-icon-sm app-icon-placeholder"><i class="fa fa-cube"></i></span>';
        var meta = [];
        if (p.category) meta.push('分类：' + esc(p.category));
        if (p.developer) meta.push('开发者：' + esc(p.developer));
        if (p.latestVersionName) meta.push('最新版本：v' + esc(p.latestVersionName) + (p.sizeMb ? '（' + p.sizeMb + ' MB）' : ''));
        var html = '' +
            '<div class="border rounded p-2">' +
            '  <div class="d-flex align-items-center mb-2">' + icon +
            '    <div class="ml-2"><strong>' + esc(p.name || p.packageName) + '</strong>' +
            '      <div class="text-muted small"><code>' + esc(p.packageName || '') + '</code></div></div>' +
            (p.source ? '<span class="badge badge-info ml-2 align-self-start">' + esc(p.source) + '</span>' : '') +
            '</div>' +
            (p.summary ? '<div class="small text-muted mb-2">' + esc(p.summary) + '</div>' : '') +
            (meta.length ? '<div class="small mb-2">' + meta.map(function (m) { return '<span class="mr-3">' + m + '</span>'; }).join('') + '</div>' : '') +
            (p.apkUrl ? '<div class="small mb-2">APK：<a href="' + esc(p.apkUrl) + '" target="_blank" rel="noopener">' + esc(p.apkUrl) + '</a></div>' : '') +
            '<button class="btn btn-success btn-sm" onclick="Admin.importExternal(\'' + esc(p.packageName || '') + '\',\'' + esc(p.source || '') + '\')"><i class="fa fa-download"></i> 一键导入</button>' +
            '</div>';
        // 预览与「逐源明细」分开：先看结果，再看每个源各自的表现
        $('#extResult').html(html + renderProbes(data.probes, data.overseasQueried));
    }

    function renderSearchList(res) {
        var data = (res && res.data) || {};
        var list = data.items || [];
        if (!list.length) {
            // 空结果时：若后端给出了明确的提示（如网络不可达）则展示该提示，
            // 否则一律显示「未找到匹配的应用」，避免把信封默认 msg「请求成功」误显成结果。
            var msg = (res && res.msg) || '';
            if (!msg || msg === '请求成功') {
                msg = '未找到匹配的应用。国内应用可试试常见名称（微信 / 抖音 / 高德地图…），' +
                    '冷门应用请改用「从 APK 导入」上传安装包解析包名';
            }
            $('#extResult').html('<div class="alert alert-warning py-2 mb-0">' + esc(msg) + '</div>' +
                renderProbes(data.probes, data.overseasQueried));
            return;
        }
        // 按**上游**分区渲染：每个源单独一块，一眼看出候选各自来自哪里、每个源给了几条。
        // 区块标题同时带「国内源 / 国外备选源」徽章以保留分组信息；国外源默认折叠（多为降级结果）。
        var order = [];
        var bySource = {};
        list.forEach(function (p) {
            var k = p.source || '未知来源';
            if (!bySource[k]) { bySource[k] = []; order.push(k); }
            bySource[k].push(p);
        });
        var blocks = order.map(function (k) {
            var items = bySource[k];
            var g = groupKeyOf(items[0].group);
            var meta = GROUP_META[g];
            var rows = items.map(function (p) {
                var icon = p.iconSrc
                    ? '<img class="app-icon-sm" src="' + esc(p.iconSrc) + '" alt="" onerror="appIconFallbackSm(this)"/>'
                    : '<span class="app-icon-sm app-icon-placeholder"><i class="fa fa-cube"></i></span>';
                // 区块标题已标明来源，行内不再重复 source 徽章
                return '<div class="border rounded p-2 mb-2 d-flex align-items-center">' +
                    icon +
                    '<div class="ml-2 flex-grow-1" style="min-width:0">' +
                    '  <div><strong>' + esc(p.name || p.packageName) + '</strong> <code class="small text-muted">' + esc(p.packageName || '') + '</code></div>' +
                    (p.summary ? '<div class="small text-muted text-truncate">' + esc(p.summary) + '</div>' : '') +
                    (p.latestVersionName ? '<div class="small text-muted">最新版本：v' + esc(p.latestVersionName) + '</div>' : '') +
                    '</div>' +
                    '<button class="btn btn-success btn-sm ml-2 flex-shrink-0" onclick="Admin.importExternal(\'' + esc(p.packageName || '') + '\',\'' + esc(p.source || '') + '\')"><i class="fa fa-download"></i> 导入</button>' +
                    '</div>';
            }).join('');
            var open = g === 'DOMESTIC' ? ' open' : '';
            return '<details class="mb-2"' + open + '>' +
                '<summary class="small font-weight-bold" style="cursor:pointer">' +
                '<span class="badge badge-info">' + esc(k) + '</span> ' +
                '<span class="badge ' + meta.badge + '">' + meta.title + '</span> ' +
                '<span class="text-muted">' + items.length + ' 个候选</span></summary>' +
                '<div class="mt-2">' + rows + '</div>' +
                '</details>';
        }).join('');
        // 候选列表与「逐源明细」分开渲染
        $('#extResult').html(blocks + renderProbes(data.probes, data.overseasQueried));
    }

    /** 判断输入是包名/商店链接（走精确查询），还是自由文本应用名（走模糊搜索） */
    function isPackageOrUrl(raw) {
        if (/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(raw)) return true;
        if (raw.indexOf('/') >= 0) return true;   // 含路径，视为商店链接
        if (/[?&]id=/.test(raw)) return true;       // Google Play 等 ?id=
        return false;
    }

    // ------------------------------------------------------------ 导入链路实时追踪（SSE）
    // 旧实现：一次 POST 同步等待最长 30s，期间无任何状态，失败只给一句笼统提示。
    // 新实现：POST 启动任务立即拿 taskId → EventSource 订阅流，逐条实时展示每一步链路。
    var traceSource = null;    // 当前 EventSource
    var traceTimer = null;     // 耗时计时器
    var traceStartAt = 0;
    var traceFinished = false; // 任务是否已结束（用于判断提前关闭时的提示）

    function closeTraceStream() {
        if (traceSource) { try { traceSource.close(); } catch (e) { /* ignore */ } traceSource = null; }
        if (traceTimer) { clearInterval(traceTimer); traceTimer = null; }
    }

    /** 单条步骤 → 一行（左侧色条按状态着色，含序号 / 阶段 / 上游 / 明细 / 耗时） */
    function traceStepRow(s) {
        var ms = (s.durationMs != null) ? '<span class="ts-ms">' + s.durationMs + 'ms</span>' : '';
        var up = s.upstream ? '<span class="ts-up">' + esc(s.upstream) + '</span>' : '';
        return '<div class="trace-step trace-st-' + esc(s.status) + '">' +
            '<span class="ts-seq">' + (s.seq || '') + '</span>' +
            '<span class="ts-body"><span class="ts-phase">' + esc(s.phase) + '</span>' + up +
            '<span class="ts-detail">' + esc(s.detail) + '</span>' + ms + '</span></div>';
    }

    function renderTraceSteps(steps) {
        var html = (steps || []).map(traceStepRow).join('');
        $('#traceSteps').html(html ||
            '<div class="trace-step trace-placeholder"><span class="ts-body text-muted">已启动，等待后端推送链路…</span></div>');
        scrollTraceToBottom();
    }

    function scrollTraceToBottom() {
        var box = document.getElementById('traceSteps');
        if (box) box.scrollTop = box.scrollHeight;
    }

    /**
     * 启动一个「带链路追踪」的导入任务并打开实时面板。
     * kind: 'external-import'（需 packageName，可选 source）| 'batch-complete'
     */
    function startTracedImport(kind, packageName, source) {
        var body = { kind: kind };
        if (packageName) body.packageName = packageName;
        if (source) body.source = source;
        // 只负责「启动」，立刻返回；真正的耗时过程由 SSE 流实时回传
        ajax('POST', API + '/import-tasks', body, 15000).done(function (res) {
            var ref = (res && res.data) || {};
            if (!ref.taskId) { notify('warn', (res && res.msg) || '启动导入任务失败'); return; }
            openTraceModal(ref.title || kind, ref.taskId);
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '启动导入任务失败'));
        });
    }

    function openTraceModal(title, taskId) {
        closeTraceStream();
        traceStartAt = Date.now();
        $('#traceTitle').text(title);
        $('#traceStatus').html('<span class="text-primary"><i class="fa fa-spinner fa-spin"></i> 进行中…</span>');
        $('#traceSteps').html('<div class="trace-step trace-placeholder"><span class="ts-body text-muted">已启动，等待后端推送链路…</span></div>');
        // 允许随时关闭（批量补全可能持续 1~2 分钟，不强制等待）；任务在后台继续跑
        $('#btnTraceDone').prop('disabled', false);
        $('#traceElapsed').text('');
        traceFinished = false;
        $('#importTraceModal').modal('show');
        $('#importTraceModal').off('hidden.bs.modal.trace').on('hidden.bs.modal.trace', function () {
            closeTraceStream();
            if (!traceFinished) notify('warn', '导入任务仍在后台执行，稍后刷新列表查看结果');
        });

        var src = new EventSource(API + '/import-tasks/' + encodeURIComponent(taskId) + '/stream');
        traceSource = src;

        // 首帧：回放订阅前已产生的步骤（避免「半路才订阅」丢步骤）
        src.addEventListener('snapshot', function (e) {
            renderTraceSteps(JSON.parse(e.data).steps);
        });
        // 增量：每完成一步推一条
        src.addEventListener('step', function (e) {
            $('#traceSteps').children('.trace-placeholder').remove();
            $('#traceSteps').append(traceStepRow(JSON.parse(e.data)));
            scrollTraceToBottom();
        });
        // 结束：渲染最终快照、给出成功/失败结论、刷新列表
        src.addEventListener('end', function (e) {
            var snap = JSON.parse(e.data);
            renderTraceSteps(snap.steps);
            traceFinished = true;
            var ok = snap.status === 'DONE';
            $('#traceStatus').html(ok
                ? '<span class="text-success"><i class="fa fa-check-circle"></i> 完成：<b>' + esc(snap.message || '') + '</b></span>'
                : '<span class="text-danger"><i class="fa fa-times-circle"></i> 失败：<b>' + esc(snap.message || '') + '</b></span>');
            $('#btnTraceDone').prop('disabled', false);
            var sec = Math.round((Date.now() - traceStartAt) / 1000);
            $('#traceElapsed').text('共耗时 ' + sec + 's');
            closeTraceStream();
            if (ok) {
                loadApps();
                if (snap.kind === 'external-import') $('#externalModal').modal('hide');
            }
        });
        src.onerror = function () {
            // EventSource 自带重连；end 后我们已主动 close，此处的 error 多为正常断连，不打扰用户
            if (!traceSource) return;
            $('#traceElapsed').append(' <span class="text-muted">（连接中断，重连中…）</span>');
        };

        traceTimer = setInterval(function () {
            $('#traceElapsed').text('已耗时 ' + Math.round((Date.now() - traceStartAt) / 1000) + 's');
        }, 1000);
    }

    function importExternal(packageName, source) {
        if (!packageName) return;
        // 不再同步等待 30s：改为启动任务 + SSE 实时展示完整链路
        startTracedImport('external-import', packageName, source);
    }

    // ------------------------------------------------------------ 从 APK 导入
    var APK_MAX_BYTES = 200 * 1024 * 1024;   // APK 大小上限，避免大文件卡死浏览器
    var ICON_MAX_BYTES = 100 * 1024;         // 图标超过该值仅预览、不入库
    var APK_PARSER_SRC = (window.ctx || '') + '/vendor/app-info-parser/app-info-parser.min.js';
    var apkParsed = null;                    // 当前解析结果（待提交）

    /** 懒加载解析库（约 459KB），避免拖慢管理后台首屏 */
    function loadApkParser() {
        if (window.AppInfoParser) return $.Deferred().resolve().promise();
        return $.getScript(APK_PARSER_SRC);
    }

    function openApkModal() {
        apkParsed = null;
        $('#apkFile').val('');
        $('#apkStatus').empty();
        $('#apkResult').empty();
        $('#btnApkImport').prop('disabled', true).html('<i class="fa fa-download"></i> 确认导入');
        $('#apkModal').modal('show');
        loadApkParser();   // 预加载，用户选文件时即可用
    }

    function onApkSelected() {
        var input = $('#apkFile')[0];
        var file = input && input.files && input.files[0];
        apkParsed = null;
        $('#apkResult').empty();
        $('#btnApkImport').prop('disabled', true);
        if (!file) { $('#apkStatus').empty(); return; }
        if (file.size > APK_MAX_BYTES) {
            $('#apkStatus').html('<span class="text-danger">文件过大（超过 200MB），请选择更小的 APK</span>');
            return;
        }
        $('#apkStatus').html('<span class="text-muted"><i class="fa fa-spinner fa-spin"></i> 正在加载解析库…</span>');
        loadApkParser().done(function () {
            $('#apkStatus').html('<span class="text-muted"><i class="fa fa-spinner fa-spin"></i> 正在解析 APK…</span>');
            new window.AppInfoParser(file).parse().then(function (r) {
                renderApkResult(r, file);
            }).catch(function () {
                $('#apkStatus').html('<span class="text-danger">APK 解析失败：文件可能损坏或经过加固</span>');
            });
        }).fail(function () {
            $('#apkStatus').html('<span class="text-danger">解析库加载失败，请检查网络或联系管理员</span>');
        });
    }

    function renderApkResult(r, file) {
        var pkg = r && r.package;
        if (!pkg) {
            $('#apkStatus').html('<span class="text-danger">未能解析出包名，无法导入</span>');
            return;
        }
        var label = (r.application && r.application.label) || pkg;
        var icon = r.icon || '';
        var iconTooLarge = !!icon && icon.length > ICON_MAX_BYTES;
        apkParsed = {
            packageName: pkg,
            name: label,
            versionName: r.versionName || null,
            versionCode: (r.versionCode != null ? Number(r.versionCode) : null),
            iconDataUri: iconTooLarge ? null : (icon || null),
            sizeMb: file && file.size ? Math.round(file.size / 1024 / 1024) : null
        };
        var rows = [
            ['包名', pkg],
            ['应用名', label],
            ['版本', (r.versionName || '-') + (r.versionCode != null ? '（' + r.versionCode + '）' : '')],
            ['大小', apkParsed.sizeMb != null ? (apkParsed.sizeMb + ' MB') : '-']
        ];
        var html = '<div class="border rounded p-2 d-flex align-items-center">' +
            (icon
                ? '<img class="app-icon-sm" src="' + esc(icon) + '" alt=""/>'
                : '<span class="app-icon-sm app-icon-placeholder"><i class="fa fa-cube"></i></span>') +
            '<div class="ml-2">' + rows.map(function (x) {
                return '<div class="small"><span class="text-muted">' + esc(x[0]) + '：</span>' + esc(x[1]) + '</div>';
            }).join('') + '</div></div>' +
            (iconTooLarge ? '<div class="small text-warning mt-1">图标过大（超过 100KB），仅预览不入库</div>' : '');
        $('#apkResult').html(html);
        $('#apkStatus').html('<span class="text-success">解析完成，请确认后导入</span>');
        $('#btnApkImport').prop('disabled', false);
    }

    function importFromApk() {
        if (!apkParsed || !apkParsed.packageName) { notify('warn', '请先选择并解析 APK'); return; }
        var $btn = $('#btnApkImport');
        $btn.prop('disabled', true).html('<i class="fa fa-spinner fa-spin"></i> 导入中…');
        // 导入需写库并补商店源，比只读查询耗时，单独给 30s
        ajax('POST', API + '/import-from-apk', apkParsed, 30000).done(function (res) {
            if (res && res.data) {
                notify('ok', res.msg || '导入成功');
                $('#apkModal').modal('hide');
                loadApps();
            } else {
                notify('warn', (res && res.msg) || '导入失败');
            }
        }).fail(function (xhr, textStatus) {
            var msg = (textStatus === 'timeout') ? '导入超时，请稍后重试' : errMsg(xhr, '导入失败');
            notify('error', msg);
        }).always(function () {
            $btn.prop('disabled', false).html('<i class="fa fa-download"></i> 确认导入');
        });
    }

    window.Admin = {
        refresh: loadApps,
        collect: collect,
        batchCompleteMyapp: batchCompleteMyapp,
        openAppModal: openAppModal,
        saveApp: saveApp,
        deleteApp: deleteApp,
        openManage: openManage,
        openVersionModal: openVersionModal,
        saveVersion: saveVersion,
        deleteVersion: deleteVersion,
        openSourceModal: openSourceModal,
        saveSource: saveSource,
        deleteSource: deleteSource,
        openExternalModal: openExternalModal,
        lookupExternal: lookupExternal,
        importExternal: importExternal,
        openApkModal: openApkModal,
        importFromApk: importFromApk
    };
})(jQuery);
