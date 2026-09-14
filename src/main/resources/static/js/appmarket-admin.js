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

    // ------------------------------------------------------------ sync store sources
    function syncStoreSources() {
        var $btn = $('#btnSyncStore');
        $btn.prop('disabled', true).html('<i class="fa fa-spinner fa-spin"></i> 同步中…');
        ajax('POST', API + '/sync-store-sources').done(function (res) {
            var d = (res && res.data) || {};
            var html = '<p class="mb-2">已处理 <b>' + (d.appsProcessed || 0) + '</b> 个应用，' +
                '本次新增第三方商店源（应用宝） <b>' + (d.sourcesAdded || 0) + '</b> 条。</p>' +
                '<p class="small text-muted mb-0">已存在的源会自动跳过，可重复点击。</p>';
            $('#collectBody').html(html);
            $('#collectModal').modal('show');
            loadApps();
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '同步商店源失败'));
        }).always(function () {
            $btn.prop('disabled', false).html('<i class="fa fa-store"></i> 同步商店源');
        });
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
        // 只读查询，上游不可达/极慢时 15s 超时快速失败，避免按钮一直卡在「查询中…」
        $.ajax({
            url: API + endpoint,
            type: 'GET',
            data: { packageName: raw, keyword: raw },
            dataType: 'json',
            timeout: 15000
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

    function renderLookupPreview(res) {
        var p = res && res.data;
        if (!p) {
            $('#extResult').html('<div class="alert alert-warning py-2 mb-0">' + esc((res && res.msg) || '未找到该外部应用') + '</div>');
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
        $('#extResult').html(html);
    }

    function renderSearchList(res) {
        var list = (res && res.data) || [];
        if (!list.length) {
            // 空结果时：若后端给出了明确的提示（如网络不可达）则展示该提示，
            // 否则一律显示「未找到匹配的应用」，避免把信封默认 msg「请求成功」误显成结果。
            var msg = (res && res.msg) || '';
            if (!msg || msg === '请求成功') msg = '未找到匹配的应用，换个关键词试试';
            $('#extResult').html('<div class="alert alert-warning py-2 mb-0">' + esc(msg) + '</div>');
            return;
        }
        var html = list.map(function (p) {
            var icon = p.iconSrc
                ? '<img class="app-icon-sm" src="' + esc(p.iconSrc) + '" alt="" onerror="appIconFallbackSm(this)"/>'
                : '<span class="app-icon-sm app-icon-placeholder"><i class="fa fa-cube"></i></span>';
            return '<div class="border rounded p-2 mb-2 d-flex align-items-center">' +
                icon +
                '<div class="ml-2 flex-grow-1" style="min-width:0">' +
                '  <div><strong>' + esc(p.name || p.packageName) + '</strong> <code class="small text-muted">' + esc(p.packageName || '') + '</code>' +
                (p.source ? ' <span class="badge badge-info ml-1">' + esc(p.source) + '</span>' : '') + '</div>' +
                (p.summary ? '<div class="small text-muted text-truncate">' + esc(p.summary) + '</div>' : '') +
                (p.latestVersionName ? '<div class="small text-muted">最新版本：v' + esc(p.latestVersionName) + '</div>' : '') +
                '</div>' +
                '<button class="btn btn-success btn-sm ml-2 flex-shrink-0" onclick="Admin.importExternal(\'' + esc(p.packageName || '') + '\',\'' + esc(p.source || '') + '\')"><i class="fa fa-download"></i> 导入</button>' +
                '</div>';
        }).join('');
        $('#extResult').html(html);
    }

    /** 判断输入是包名/商店链接（走精确查询），还是自由文本应用名（走模糊搜索） */
    function isPackageOrUrl(raw) {
        if (/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(raw)) return true;
        if (raw.indexOf('/') >= 0) return true;   // 含路径，视为商店链接
        if (/[?&]id=/.test(raw)) return true;       // Google Play 等 ?id=
        return false;
    }

    function importExternal(packageName, source) {
        if (!packageName) return;
        var url = API + '/external-import?packageName=' + encodeURIComponent(packageName);
        if (source) url += '&source=' + encodeURIComponent(source);
        // 导入需从上游拉取元数据/索引，比只读查询更耗时，给 30s（服务端单次请求上限 10s/Izzy 15s）
        ajax('POST', url, null, 30000).done(function (res) {
            if (res && res.data) {
                notify('ok', '已导入：' + (res.data.name || packageName));
                $('#externalModal').modal('hide');
                loadApps();
            } else {
                notify('warn', (res && res.msg) || '导入失败');
            }
        }).fail(function (xhr) {
            notify('error', errMsg(xhr, '导入失败'));
        });
    }

    window.Admin = {
        refresh: loadApps,
        collect: collect,
        syncStoreSources: syncStoreSources,
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
        importExternal: importExternal
    };
})(jQuery);
