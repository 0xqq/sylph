/**
 * Created by Polar on 2017/12/14.
 */
/*获取URL中的参数值*/
function getUrlParam(paramName) {
    var arrSource = [];
    var paramValue = '';
    //获取url"?"后的查询字符串
    var search_url = location.search;

    if (search_url.indexOf('?') == 0 && search_url.indexOf('=') > 1) {
        arrSource = decodeURI(search_url).substr(1).split("&");
        //遍历键值对
        for (var i = 0; i < arrSource.length; i++) {
            if (arrSource[i].indexOf('=') > 0) {
                if (arrSource[i].split('=')[0].toLowerCase() == paramName.toLowerCase()) {
                    paramValue = arrSource[i].split("=")[1];
                    break;
                }
            }
        }
    }
    return paramValue;
}

window.onload = function () {
    var mime = 'text/x-sylph';
    // get mime type
    if (window.location.href.indexOf('mime=') > -1) {
        mime = window.location.href.substr(window.location.href.indexOf('mime=') + 5);
    }
    window.editor = CodeMirror.fromTextArea(document.getElementById('code'), {
        mode: mime,
        indentWithTabs: true,
        smartIndent: true,
        lineNumbers: true,
        matchBrackets: true,
        autofocus: true,
        extraKeys: {"Ctrl-Space": "autocomplete"},
        hintOptions: {
            tables: {
                users: {name: null, score: null, birthDate: null},
                countries: {name: null, population: null, size: null}
            }
        }
    });

    // window.explanResult = CodeMirror.fromTextArea(document.getElementById('explanResult'), {
    //     mode: 'application/json',
    //     indentWithTabs: true,
    //     smartIndent: true,
    //     lineNumbers: true,
    //     matchBrackets: true
    // });
};

/*页面加载*/
$(function () {
    /*add or edit*/
    var type = getUrlParam("type");
    if (type == "add") {
        $("input,textarea").val('');
    } else if (type == "edit") {
        $.ajax({
            url: "/_sys/stream_sql/get?jobId=" + getUrlParam("jobId"),
            type: "get",
            dataType: "json",
            data: {},
            cache: false,
            success: function (result) {
                $("textarea[name=jobId]").val(result.jobId);
                $("textarea[name=query]").val(result.graph.flowString);
                var files = result.files;
                for(var i = 0; i < files.length; i++) {
                    $('#fileList').append(
                        '<div class="file_row" id="file_' + files[i] + '">' +
                        '<input type="hidden" name="selectFile" value="' + files[i] + '" />' +
                        '<i class="fa fa-trash" onclick="deleteFile(this)"></i>' +
                        '<span class="file_name">'+files[i]+'</span>' +
                        '</div>');
                }
            }
        });
    }

    $('#submit').click(function () {
        var formData = new FormData($('form')[0]);
        $.ajax({
            url: '/_sys/stream_sql/save',
            type: 'POST',
            cache: false,
            data: formData,
            processData: false,
            contentType: false
        }).done(function(data) {
            if (data.status == "ok") {
                alert("保存成功");
                window.location.href = "index.html";
            } else {
                alert(data.msg);
            }
        }).fail(function(data) {
            alert(data.msg);
        });
    });

    $('input[name=file]').change(function(){
        $('#fileList').children().remove();
        var files = $(this).prop('files');
        for(var i = 0; i < files.length; i++) {
            $('#fileList').append(
                '<div class="file_row" id="file_' + files[i].name + '">' +
                '<input type="hidden" name="selectFile" value="' + files[i].name + '" />' +
                '<i class="fa fa-trash" onclick="deleteFile(this)"></i>' +
                '<span class="file_name">'+files[i].name+'</span>' +
                '</div>');
        }
    });
});

function deleteFile(obj) {
    $(obj).parent().remove();
}

var UploadFilesLayer;
function openUploadFilesLayer() {
    UploadFilesLayer = layer.open({type: 1,area: ['500px', '360px'],title: '文件上传',shade: 0.6,maxmin: false,
        anim: 1,content: $('#upload-files')  });
}

var ConfigSetLayer;
function openConfigSetLayer() {
    ConfigSetLayer = layer.open({type: 1,area: ['500px', '360px'],title: '高级配置',shade: 0.6,maxmin: false,
        anim: 1,content: $('#config-set')  });
}