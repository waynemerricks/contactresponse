<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/inbox.php'); //Functions for getting the inbox of this user
  include('includes/user.php'); //Functions relating to logged in user
  include('includes/autoreplies.php'); //Functions relating to auto replies

  $autoReplies = getAutoReplyTypes($mysqli);
  $contactsPending = getInbox(getUserId(), isDefaultHelper(), $mysqli);

?>
<html>
  <head>
    <meta charset="utf8">
    <title>CRS | Inbox</title>
    <link rel="stylesheet" type="text/css" href="css/crs.css">
    <link rel="stylesheet" type="text/css" href="css/menu.css">
    <script type="text/javascript" src="includes/jquery-2.1.1.min.js"></script>
    <script type="text/javascript">
      /** JS Flags this contact for a quick generic auto reply
       * @param contact contact id to reply to
       * @param maxid id of latest message this applies to
       */
      function replyGeneric(contact, maxid){

        autoReply(contact, '<?php echo $autoReplies['G']; ?>', maxid);

      }

      /** JS Flags this contact for a quick prayer auto reply
       * @param contact contact id to reply to
       * @param maxid id of latest message this applies to
       */
      function replyPrayer(contact, maxid){

        autoReply(contact, '<?php echo $autoReplies['P']; ?>', maxid);

      }

      /** JS Flags this contact for a quick song auto reply
       * @param contact contact id to reply to
       * @param maxid id of latest message this applies to
       */
      function replySong(contact, maxid){

        autoReply(contact, '<?php echo $autoReplies['S']; ?>', maxid);

      }

      /** JS Flags this contact for a quick song auto reply
       * @param contact contact id to reply to
       * @param maxid id of latest message this applies to
       */
      function replyCompetition(contact, maxid){

        autoReply(contact, '<?php echo $autoReplies['C']; ?>', maxid);

      }

      /** JS Flags this contact for a quick auto reply
       * @param contact contact id to reply to
       * @param type type of reply to send
       * @param maxid id of latest message this applies to
       */
      function autoReply(contact, replyType, maxid){

        if(confirm('Auto Reply to this contact?')){

          //JQuery POST to autoreply.php
          $.post( "dynamic/autoreply.php", { template: replyType, contact: contact, newest: maxid })
            .done(function( data ) {
              if(data == 'SUCCESS')//It worked so remove this entry from inbox
                $("#contact-" + contact).remove();
              else alert(data);
            });

        }

      }

      /** JS Sets this contact as sending junk, no more messages will come
       * from this contact
       */
      function junkMail(contact){

        if(confirm('Mark as Junk Mail? This will block this sender!')){

          //JQuery POST to junkmail.php
          $.post( "dynamic/junkmail.php", { contact: contact})
            .done(function( data ) {
              if(data == 'SUCCESS')//It worked so remove this entry from the inbox
                $("#contact-" + contact).remove();
              else alert(data);
            });

        }

      }
    </script>
  </head>
  <body>
    <div class="inbox">
      <table class="inbox">
        <tr class="header">
          <th>&nbsp;</th>
          <th class="centre">Date</th>
          <th>Contact</th>
          <th>Preview</th>
        </tr>
        <?php if(sizeof($contactsPending) == 0){ ?>
          <tr><td colspan="4">There are no messages in your inbox</td></tr>
        <?php
          } else {

            $lastMessageID = -1;

            for($i = 0; $i < sizeof($contactsPending); $i++){ 

              if($contactsPending[$i]['maxid'] > $lastMessageID)
                $lastMessageID = $contactsPending[$i]['maxid'];

              $previewIsFullMessage = false;

              if(substr($contactsPending[$i]['preview'], -3) != '...')
                $previewIsFullMessage = true;

              $src = 'images/generic.png';

              if($contactsPending[$i]['type'] == 'E')
                $src = 'images/email.png';
              else if($contactsPending[$i]['type'] == 'P')
                $src = 'images/phone.png';
              else if($contactsPending[$i]['type'] == 'S')
                $src = 'images/sms.png';

              $r = 31;
              $g = 131;
              $b = 23;

              $waiting = $contactsPending[$i]['waiting'];

              if($waiting > 99){

                $r = 172;
                $g = 0;
                $b = 0;

              }else if($waiting > 1 && $waiting < 51){

                $r += round($waiting * 4.57);
                $g += round($waiting * 1.53);
                $b += round($waiting * 0.28);

              }else if($waiting != 1){

                //Start from yellow
                $r = 255 + round($waiting * -1.69);
                $g = 206 + round($waiting * -4.2);
                $b = 9 + round($waiting * -0.18);

              }

              $rgb = $r . ', ' . $g . ', ' . $b;

              if($contactsPending[$i]['waiting'] > 99)
                $contactsPending[$i]['waiting'] = '++';
        ?>
          <tr id="contact-<?php echo $contactsPending[$i]['id']; ?>">
            <td class="icon">
              <div id="container">
                <div id="icon">
                  <a href="viewPending.php?id=<?php echo $contactsPending[$i]['id']; ?>">
                    <img class="icon" src="<?php echo $src; ?>" />
                  </a>
                </div>
                <div id="badge" style="background-color: rgb(<?php echo $rgb; ?>)">
                  <?php echo $contactsPending[$i]['waiting']; ?>
                </div>
              </div>
            </td>
            <td class="time centre">
              <a href="viewPending.php?id=<?php echo $contactsPending[$i]['id']; ?>">
                <?php echo $contactsPending[$i]['date']; ?>
              </a>
            </td>
            <td class="name">
              <a href="viewPending.php?id=<?php echo $contactsPending[$i]['id']; ?>">
                <?php echo $contactsPending[$i]['name']; ?>
              </a>
            </td>
            <td class="preview">
              <a href="viewPending.php?id=<?php echo $contactsPending[$i]['id']; ?>">
                <?php echo $contactsPending[$i]['preview']; ?>
              </a>
              <?php if(canReplyFromInbox()){ ?>
                <div class="quickTools">
                  <?php if($contactsPending[$i]['waiting'] == 1 && $previewIsFullMessage){ //Only show quick tools if there is one reply ?>
                    <a id="replygeneric-<?php echo $contactsPending[$i]['id']; ?>" href="javascript:void(0)" onclick="replyGeneric('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')">
                      <img alt="Generic" src="images/toolgeneric.png" />
                    </a>
                    <a id="replyprayer-<?php echo $contactsPending[$i]['id']; ?>" href="javascript:void(0)" onclick="replyPrayer('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')">
                      <img alt="Prayer" src="images/prayer.png" />
                    </a>
                    <a id="replysong-<?php echo $contactsPending[$i]['id']; ?>" href="javascript:void(0)" onclick="replySong('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')">
                      <img alt="Song" src="images/song.png" />
                    </a>
                    <a id="replycompetition-<?php echo $contactsPending[$i]['id']; ?>" href="javascript:void(0)" onclick="replyCompetition('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')">
                      <img alt="Competition" src="images/competition.png" />
                    </a>
                  <?php } ?>
                  <?php if(canJunk()){ ?>
                    <a href="javascript:void(0)" onclick="junkMail('<?php echo $contactsPending[$i]['id']; ?>')">
                      <img alt="Mark as Junk" src="images/junk.png" />
                    </a>
                  <?php } ?>
                </div>
              <?php } ?>
            </td>
          </tr>
        <?php
            }
          }
        ?>
      </table>
    </div>
    <div id="spacer"></div>
    <div id="footer">
      <ul id="footer_menu"><!-- Menu Root -->
        <li class="buttons homeButton"><a href="main.php"></a></li>
        <li class="buttons contactButton"><a href="contacts.php"></a></li>
        <li><a href="#"><?php echo getUsersName(); ?></a>
        <?php if(isAdmin()){ ?>
          <li><a href="#">Admin</a>
            <ul class="dropup">
              <?php if(canCreateUsers()){ ?>
                <li><a href="admin/createUser.php">Create User</a></li>
              <?php } ?>
              <?php if(canEditPermissions()){ ?>
                <li><a href="#">Permissions</a></li>
              <?php } ?>
              <?php if(canEditRoles()){ ?>
                <li><a href="#">Roles</a></li>
              <?php } ?>
            </ul>
          </li>
        <?php } ?>
        <li class="right"><a href="logout.php">Logout</a></li>
      </ul>
    </div>
    <div id="hidden">
      <input type="hidden" id="lastMessageID" value="<?php echo $lastMessageID; ?>" />
    </div>
    <script type="text/javascript">
      /* Needs to be here because we have to loop through to get lastMessageID
       * Its pointless doing two loops to achieve this
       */
      //JQuery updateInbox messages
      function updateInbox(){
        //Get last message ID from the hidden lastMessageID input field
        var lastMessage = $('input[type=hidden]#lastMessageID').val();

        lastMessage = 1820;//TODO Remove debug
        $.get( "dynamic/updateinbox.php?lastMessage=" + lastMessage,
              function( data ) {

          var needle = 'SUCCESS';

          if(data.slice(0, needle.length) == needle){

            var json = data.substring(needle.length, data.length);
            var newMessages = $.parseJSON( json );
            var lastMessage = -1;

            for(i = 0; i < newMessages.length; i++){

              //Store Biggest lastMessage
              if(lastMessage < newMessages[i].maxid)
                lastMessage = newMessages[i].maxid;

              //Update message waiting count
              var id = newMessages[i].id;

              if ($('#contact-' + id).find('#badge').length > 0) { 

                //If element exists, update it
                var messages = $('#contact-' + id).find('#badge').html().trim();
                messages = Number(messages) + Number(newMessages[i].waiting);
                $('#contact-' + id).find('#badge').html(messages);

                //Update the quick tools links
                $('#replygeneric-' + id).attr('onclick',"replyGeneric('" + id + "', '" + newMessages[i].maxid + "')");
                $('#replyprayer-' + id).attr('onclick',"replyPrayer('" + id + "', '" + newMessages[i].maxid + "')");
                $('#replysong-' + id).attr('onclick',"replySong('" + id + "', '" + newMessages[i].maxid + "')");
                $('#replycompetition-' + id).attr('onclick',"replyCompetition('" + id + "', '" + newMessages[i].maxid + "')");
                $('#contact-' + id).fadeOut('fast').fadeIn('fast');

              }else{

                //Doesn't exist so make a new one
                //TODO Flash row for visual effect if anyones looking

              }

              //Update lastMessage
              if(lastMessage != -1)
                $('input[type=hidden]#lastMessageID').val(lastMessage);

            }

          }else
            alert(data);

        });

      }

      //TODO Change back to setInterval
      var updateTimer = setTimeout(function(){ updateInbox(); }, 5000);
    </script>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>