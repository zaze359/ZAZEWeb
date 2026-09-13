/**
 * 门户公共脚本：退出登录。
 *
 * 依赖 common/head 里注入的全局 ctx（项目根路径）。
 */
$(function () {
    $('#btn-logout').on('click', function () {
        $.ajax({
            url: ctx + '/api/v1/auth/logout',
            type: 'POST',
            dataType: 'json'
        }).always(function () {
            window.location.href = ctx + '/login';
        });
    });
});
