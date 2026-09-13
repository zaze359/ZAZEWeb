/**
 * 登录页。
 *
 * 登录接口刻意不加 @LoggerManage，明文密码不会进日志；这里也只是 POST 一次即丢弃。
 */
$(function () {
    $('#btn-login').on('click', doLogin);
    $('#login-form input').on('keydown', function (e) {
        if (e.key === 'Enter') {
            doLogin();
        }
    });
});

function doLogin() {
    var username = $.trim($('#username').val());
    var password = $('#password').val();

    var $alert = $('#login-alert');
    if (!username || !password) {
        $alert.text('请输入用户名和密码').removeClass('d-none');
        return;
    }

    $('#btn-login').prop('disabled', true).text('登录中...');

    $.ajax({
        url: ctx + '/api/v1/auth/login',
        type: 'POST',
        contentType: 'application/json;charset=UTF-8',
        dataType: 'json',
        data: JSON.stringify({username: username, password: password}),
        success: function (res) {
            if (res && res.code === 200 && res.data) {
                $alert.addClass('d-none');
                window.location.href = ctx + '/';
            } else {
                showError((res && res.msg) || '登录失败');
            }
        },
        error: function () {
            showError('登录请求失败，请稍后重试');
        }
    });
}

function showError(msg) {
    $('#login-alert').text(msg).removeClass('d-none');
    $('#btn-login').prop('disabled', false).text('登录');
}
