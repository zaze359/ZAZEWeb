function createFrame(url) {
    let height = document.documentElement.clientHeight - 160 + "px";
    return "<iframe onload='$(this)[0].height = document.documentElement.clientHeight-160' width='100%' height='" + height + "' frameborder='0' scrolling='auto' src=\"" + url + "\"></iframe>"
    // return '<iframe scrolling="auto" frameborder="0"  src="' + url
    //     + '" style="width:100%;height:100%;"></iframe>';
}


function addTab(url) {

}