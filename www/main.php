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
      //JQuery updateInbox messages
      function updateInbox(){

        $.get( "ajax/test.html", function( data ) {

          $( ".result" ).html( data );
          alert( "Load was performed." );

        });

      }

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

            for($i = 0; $i < sizeof($contactsPending); $i++){ 

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
                    <a href="javascript:void(0)">
                      <img alt="Generic" src="images/toolgeneric.png" onclick="replyGeneric('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')" />
                    </a>
                    <a href="javascript:void(0)" onclick="replyPrayer('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')">
                      <img alt="Prayer" src="images/prayer.png" />
                    </a>
                    <a href="javascript:void(0)" onclick="replySong('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')">
                      <img alt="Song" src="images/song.png" />
                    </a>
                    <a href="javascript:void(0)" onclick="replyCompetition('<?php echo $contactsPending[$i]['id']; ?>', '<?php echo $contactsPending[$i]['maxid']; ?>')">
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
        <li><a href="#">Test Menu</a>
          <ul class="dropup">
            <li><a href="#" onclick="">Test Item 1</a></li>
            <li><a href="#" onclick="">Test Item 2</a></li>
            <li><a href="#" onclick="">Test Item 3</a></li>
          </ul>
        </li>
        <li class="right"><a href="logout.php">Logout</a></li>
      </ul>
    </div>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>