var username = null;

$(function() {
    var setting = $('.setting-avatar');
    var spinner = setting.find('.fa-spinner');

    function reloadAvatarImg(src) {
        $('.user-avatar').css('background-image', 'url(' + src + ')');
        var alert = $('.alert-success');
        alert.find('.message').html(
            '<strong>Success!</strong> Please re-log to any Sponge services for the change to take effect.');
        alert.fadeIn('slow');
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
            data: { csrfToken: csrf },
            dataType: 'json',
            complete: function() {
                spinner.hide();
            },
            success: function(json) {
                console.log(json);
                reloadAvatarImg(json['avatar_url']);
                $('input[value="file"]').prop('checked', true);
                $('input[value="gravatar"]').prop('checked', false);
            }
        })
    });
});
