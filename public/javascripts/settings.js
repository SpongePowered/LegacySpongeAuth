var username = null;

$(function() {
    var setting = $('.setting-avatar');
    var spinner = setting.find('.fa-spinner');

    function reloadAvatarImg(src) {
        $('.user-avatar').attr('src', src + '?' + new Date().getTime());
    }

    setting.find('.btn-submit').click(function() {
        spinner.show();
        var formData = new FormData(setting.find('form')[0]);
        $.ajax({
            url: '/settings/avatar',
            data: formData,
            type: 'post',
            contentType: false,
            processData: false,
            complete: function() {
                spinner.hide();
            },
            success: function(json) {
                reloadAvatarImg(json['avatar_url']);
            }
        })
    });

    setting.find('.btn-reset').click(function() {
        spinner.show();
        $.ajax({
            url: '/settings/avatar/reset',
            type: 'post',
            dataType: 'json',
            complete: function() {
                spinner.hide();
            },
            success: function(json) {
                console.log(json);
                reloadAvatarImg(json['avatar_url']);
            }
        })
    });
});
