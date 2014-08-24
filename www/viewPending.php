<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/messages.php'); //Functions for getting messages
  include('includes/contact.php'); //Functions for getting a contact
  include('includes/user.php'); //Functions relating to logged in user
  include('includes/autoreplies.php'); //Functions relating to auto replies

  $pendingMessages = array();

  if(isset($_GET['id'])){

    $pendingMessages = getPendingMessages(getUserId(), $_GET['id'], 
        isDefaultHelper(), $mysqli);
    $lastMessageID = -1;//Set this to the id of newest message (last in array)

    $contact = new Contact($_GET['id'], $mysqli);

    $autoReply = getAutoReplyLabels($mysqli);
    $delegates = getDelegatesList($mysqli);

  }else
    header('Location: main.php');//We got here weirdly so just go back to main

?>
<html>
  <head>
    <meta charset="utf8">
    <title>CRS | <?php echo $contact->name; ?></title>
    <link rel="stylesheet" type="text/css" href="css/crs.css">
    <link rel="stylesheet" type="text/css" href="css/crs_viewPending.css">
    <link rel="stylesheet" type="text/css" href="css/menu.css">
    <script type="text/javascript" src="includes/jquery-2.1.1.min.js"></script>
    <script type="text/javascript">
      /** JS Sets this contact as auto having an auto reply
       * Will archive all but the last message, last one gets the replied status
       * Sends an auto reply based on today's date and the category selected
       */
      function autoReply(template, contact, newestID){

        //JQuery POST to autoreply.php
        $.post( "autoreply.php", { template: template, contact: contact, newest: newestID })
          .done(function( data ) {
            if(data == 'SUCCESS')//It worked so redirect to inbox
              window.location='main.php';
          });

      }

      /** JS Sets reply to manual and opens up your email client
       * Manual will time out after MANUAL_TIME_OUT and go back
       * to default D status and back into your inbox */
      function manualReply(replyType, contact, newestID){

        //JQuery goes here
        alert("Type: " + replyType + "\n" +
              "Contact: " + contact + "\n" +
              "Newest: " + newestID);

      }

      /** JS Sets this contacts assigned user and removes
       * from this person's inbox */
       function delegate(contact, delegate){

         //JQuery goes here
         alert("Contact: " + contact + "\n" +
               "Delegate: " + delegate);

       }
    </script>
  </head>
  <body>
    <div class="contact">
      <table class="contact">
       <tr>
         <td rowspan="4"><img src="images/nophoto.png" /></td>
         <th>Name:</th>
         <td class="fullWidth"><?php echo $contact->name; ?></td>
       </tr>
       <tr>
         <th>Location:</th>
         <td class="fullWidth"><?php echo $contact->getLocation(); ?></td>
       </tr>
       <tr>
         <th>Gender:</th>
         <td class="fullWidth"><?php echo $contact->gender; ?></td>
       </tr>
       <tr>
         <th>Notes:</th>
         <td class="fullWidth"><?php echo $contact->getNotes(); ?></td>
       </tr>
      </table>
    </div>
    <div class="pending">
      <table class="pending">
        <?php if(sizeof($pendingMessages) == 0){ ?>
          <tr>
            <td class="fullWidth" colspan="3">
              There are no messages for this contact
            </td>
          </tr>
        <?php
          } else {

            $mailToID = 0;

            for($i = 0; $i < sizeof($pendingMessages); $i++){ 

              $mailToID = $pendingMessages[$i]['id'];
              $src = 'images/generic.png';

              if($pendingMessages[$i]['type'] == 'E')
                $src = 'images/email.png';
              else if($pendingMessages[$i]['type'] == 'P')
                $src = 'images/phone.png';
              else if($pendingMessages[$i]['type'] == 'S')
                $src = 'images/sms.png';
        ?>
          <tr>
            <td class="icon">
              <img class="icon" src="<?php echo $src; ?>" />
            </td>
            <td class="time">
              <?php echo $pendingMessages[$i]['date']; ?>
            </td>
            <td class="subject fullWidth">
              <?php echo $pendingMessages[$i]['subject']; ?>
            </td>
          </tr>
          <tr class="message">
            <td colspan="3" class="message fullWidth">
              <?php echo nl2br($pendingMessages[$i]['message']); ?><hr>
            </td>
          </tr>
        <?php
              $lastMessageID = $pendingMessages[$i]['id'];
            }
          }
        ?>
      </table>
    </div>
    <div id="spacer"></div>
    <div id="footer">
      <ul id="footer_menu"><!-- Menu Root -->
        <li class="homeButton"><a href="main.php"></a></li>
        <li><a href="#">Manual Reply</a>
          <ul class="dropup">
            <li><a href="mailto:<?php echo $_GET['id']; ?>@crs.internal?subject=Type%20Your%20Subject%20Here&body=Type%20Your%20Message%20here" onclick="manualReply('E', '<?php echo $_GET['id']; ?>', '<?php echo $lastMessageID; ?>')">Email</a></li>
            <li><a href="mailto:<?php echo $_GET['id']; ?>@crs.internal?subject=Subject%20Will%20Be%20Ignored&body=Type%20Your%20SMS%20here" onclick="manualReply('S', '<?php echo $_GET['id']; ?>', '<?php echo $lastMessageID; ?>')">SMS</a></li>
            <li><a href="mailto:<?php echo $_GET['id']; ?>@crs.internal?subject=Subject%20Will%20Be%20Ignored&body=Type%20Your%20Letter%20here" onclick="manualReply('L', '<?php echo $_GET['id']; ?>', '<?php echo $lastMessageID; ?>')">Letter</a></li>
          </ul>
        </li>
        <li><a href="#">Auto Reply As...</a>
          <ul class="dropup">
            <?php foreach($autoReply as $category){ ?>
              <li>
                <a href="javascript:void(0)" onclick="autoReply('<?php echo $category['id']; ?>', '<?php echo $_GET['id']; ?>', '<?php echo $lastMessageID; ?>')">
                  <?php echo $category['label']; ?>
                </a>
              </li>
            <?php } ?>
          </ul>
        </li>
        <li><a href="#">Delegate to...</a>
          <ul class="dropup">
           <?php foreach($delegates as $user){ ?>
              <li>
                <a href="javascript:void(0)" onclick="delegate('<?php echo $_GET['id']; ?>', '<?php echo getLoggedInUserID(); ?>')">
                  <?php echo $user['name']; ?>
                </a>
              </li>
            <?php } ?>
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