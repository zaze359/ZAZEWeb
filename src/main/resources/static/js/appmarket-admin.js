/* 应用市场 · 管理后台脚本（jQuery + Bootstrap）
 * 提供：应用增删改、版本与下载源管理、触发自动采集。
 * 所有接口统一返回 {code,data,msg} 信封。
 */
(function ($) {
    var API = (window.ctx || '') + '/api/v1/appmarket/admin';

    var state = {
        apps: [],
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

    function ajax(method, url, data) {
        return $.ajax({
            url: url,
            type: method,
            contentType: 'application/json; charset=utf-8',
            data: data != null ? JSON.stringify(data) : undefined,
            dataType: 'json'
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
        if (!state.apps.length) {
            $('#appTbody').html('<tr><td colspan="7" class="text-center text-muted py-4">暂无应用，点击右上角「新增应用」或「触发自动采集」。</td></tr>');
            return;
        }
        var html = state.apps.map(function (app) {
            var icon = app.iconUrl
                ? '<img src="' + esc(app.iconUrl) + '" alt="" style="width:28px;height:28px;object-fit:cover;border-radius:6px" onerror="this.style.display=\'none\'"/>'
                : '';
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
    });

    window.Admin = {
        refresh: loadApps,
        collect: collect,
        openAppModal: openAppModal,
        saveApp: saveApp,
        deleteApp: deleteApp,
        openManage: openManage,
        openVersionModal: openVersionModal,
        saveVersion: saveVersion,
        deleteVersion: deleteVersion,
        openSourceModal: openSourceModal,
        saveSource: saveSource,
        deleteSource: deleteSource
    };
})(jQuery);
