var email = null;

$(function() {
    $('.btn-resend').click(function() {
        var spinner = $(this).find('fa-spinner').show();
        $.ajax({
            url: '/email/confirm/resend',
            method: 'post',
            data: {
                'email': email,
                'csrfToken': csrf
            },
            complete: function() {
                spinner.hide();
            },
            success: function() {
                var alert = $('.alert-success');
                alert.find('.message').text('Email sent.');
                alert.fadeIn('slow');
            }
        });
    });
});
