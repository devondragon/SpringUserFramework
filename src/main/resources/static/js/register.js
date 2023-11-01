var serverContext = "/";


$(document).ready(function () {
    $('form').submit(function (event) {
        register(event);
    });

    $(":password").keyup(function () {
        if ($("#password").val() != $("#matchPassword").val()) {
            $("#globalError").show().html(/*[[#{PasswordMatches.user}]]*/);
        } else {
            $("#globalError").html("").hide();
        }
    });
});

function register(event) {
    event.preventDefault();
    $("#signUpButton").attr('disabled', 'disabled');
    $(".alert").html("").hide();
    $(".error-list").html("");
    if ($("#password").val() != $("#matchPassword").val()) {
        $("#globalError").show().html(/*[[#{PasswordMatches.user}]]*/);
        return;
    }
    $.ajaxSetup({
        beforeSend: function (xhr) {
            var token = $("meta[name='_csrf']").attr("content");
            var header = $("meta[name='_csrf_header']").attr("content");
            xhr.setRequestHeader(header, token);
        }
    });
    var formData = $('form').serialize();
    $.post("/user/registration", formData, function (data) {
        if (data.success) {
            window.location.href = data.redirectUrl;
        } else {
            console.log("200 response but success = false!");
            console.log(data);
            $('#globalError').html("Error!").show();
            $("#signUpButton").removeAttr('disabled');
        }


    })
        .fail(function ($xhr) {
            var data = $xhr.responseJSON;
            console.log(JSON.stringify(data.messages));
            if (data.code == 2) {
                $('#existingAccountError').show();
            } else {
                $('#globalError').html("Error!").show();
            }
            $("#signUpButton").removeAttr('disabled');
        });
}

