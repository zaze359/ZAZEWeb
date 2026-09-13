/* 应用市场门户交互脚本（jQuery）
 * 列表页：渲染应用卡片，点击跳转详情
 * 详情页：通过全局 APP_ID 拉取应用详情，渲染版本与下载源
 */
(function ($) {
    var API = (window.ctx || '') + '/api/v1/appmarket';

    var SOURCE_CLASS = {
        GITHUB: 'btn-dark',
        FDROID: '#197278',
        OFFICIAL: 'btn-primary',
        APKMIRROR: 'btn-warning',
        COOLAPK: 'btn-info',
        MYAPP: 'btn-success',
        OTHER: 'btn-secondary'
    };

    function sourceBtnClass(type) {
        return SOURCE_CLASS[type] || 'btn-secondary';
    }

    function renderList() {
        if (!$('#app-list').length) return;
        $.getJSON(API + '/apps', function (res) {
            var apps = (res && res.data) || [];
            if (!apps.length) {
                $('#app-list').html('<div class="col-12 text-muted">暂无应用数据。</div>');
                return;
            }
            var html = apps.map(function (app) {
                var icon = app.iconUrl
                    ? '<img class="app-icon" src="' + app.iconUrl + '" alt="" onerror="this.style.display=\'none\'"/>'
                    : '<div class="app-icon d-flex align-items-center justify-content-center bg-light text-muted">APP</div>';
                return '' +
                    '<div class="col-md-4 col-sm-6 mb-3">' +
                    '  <a href="' + (window.ctx || '') + '/appmarket/' + app.id + '" class="text-decoration-none">' +
                    '    <div class="card app-card h-100">' +
                    '      <div class="card-body d-flex">' +
                    '        <div class="mr-3">' + icon + '</div>' +
                    '        <div class="flex-grow-1">' +
                    '          <h6 class="mb-1 text-dark">' + escapeHtml(app.name) + '</h6>' +
                    '          <div class="mb-1"><span class="badge badge-cat">' + escapeHtml(app.category || '未分类') + '</span></div>' +
                    '          <p class="small text-muted mb-1 text-truncate">' + escapeHtml(app.developer || '') + '</p>' +
                    '          <p class="small text-muted mb-0">' + (app.versionCount || 0) + ' 个版本</p>' +
                    '        </div>' +
                    '      </div>' +
                    '    </div>' +
                    '  </a>' +
                    '</div>';
            }).join('');
            $('#app-list').html(html);
        });
    }

    function renderDetail() {
        if (typeof APP_ID === 'undefined' || !APP_ID) return;
        $.getJSON(API + '/apps/' + APP_ID, function (res) {
            var app = res && res.data;
            if (!app) {
                $('#app-detail').html('<p class="text-danger">未找到该应用。</p>');
                return;
            }
            var icon = app.iconUrl
                ? '<img class="app-icon" src="' + app.iconUrl + '" alt="" onerror="this.style.display=\'none\'"/>'
                : '<div class="app-icon d-flex align-items-center justify-content-center bg-light text-muted">APP</div>';

            var versionsHtml = (app.versions || []).map(function (v) {
                var sources = (v.sources || []).map(function (s) {
                    return '' +
                        '<a class="btn ' + sourceBtnClass(s.sourceType) + ' mb-2 mr-2 source-btn" href="' + s.downloadUrl + '" target="_blank" rel="noopener">' +
                        escapeHtml(s.sourceName || s.sourceType || '下载') +
                        (s.region ? ' · ' + escapeHtml(s.region) : '') +
                        '</a>' +
                        (s.note ? '<div class="small text-muted mb-2">' + escapeHtml(s.note) + '</div>' : '');
                }).join('');

                return '' +
                    '<div class="ver-item">' +
                    '  <div class="d-flex justify-content-between align-items-center">' +
                    '    <strong>v' + escapeHtml(v.versionName || '') + '</strong>' +
                    '    <span class="small text-muted">' + (v.releaseDate ? fmtDate(v.releaseDate) : '') +
                    (v.sizeMb ? ' · ' + v.sizeMb + ' MB' : '') + ' · ' + (v.sourceCount || 0) + ' 来源</span>' +
                    '  </div>' +
                    (v.changelog ? '<div class="small text-muted mt-1">' + escapeHtml(v.changelog) + '</div>' : '') +
                    '  <div class="mt-2">' + (sources || '<span class="text-muted small">暂无下载源</span>') + '</div>' +
                    '</div>';
            }).join('');

            var html = '' +
                '<div class="card">' +
                '  <div class="card-body d-flex align-items-center">' +
                '    <div class="mr-3">' + icon + '</div>' +
                '    <div>' +
                '      <h4 class="mb-1">' + escapeHtml(app.name) + '</h4>' +
                '      <div class="mb-1"><span class="badge badge-cat">' + escapeHtml(app.category || '未分类') + '</span>' +
                '      <span class="ml-2 small text-muted">' + escapeHtml(app.developer || '') + '</span></div>' +
                (app.summary ? '<p class="text-muted mb-1">' + escapeHtml(app.summary) + '</p>' : '') +
                (app.officialUrl ? '<a href="' + app.officialUrl + '" target="_blank" rel="noopener" class="small">官方网站 ↗</a>' : '') +
                '    </div>' +
                '  </div>' +
                '</div>' +
                '<h5 class="mt-4 mb-3">版本与下载源</h5>' +
                (versionsHtml || '<p class="text-muted">暂无版本。</p>');

            $('#app-detail').html(html);
        });
    }

    function fmtDate(ms) {
        try {
            var d = new Date(ms);
            return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
        } catch (e) { return ''; }
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    $(function () {
        renderList();
        renderDetail();
    });
})(jQuery);
