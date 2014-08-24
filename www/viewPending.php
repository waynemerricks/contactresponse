<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/messages.php'); //Functions for getting messages
  include('includes/contact.php'); //Functions for getting a contact
  include('includes/user.php'); //Functions relating to logged in user

  $pendingMessages = array();

  if(isset($_GET['id'])){

    $pendingMessages = getPendingMessages(getUserId(), $_GET['id'], 
        isDefaultHelper(), $mysqli);
    $contact = new Contact($_GET['id'], $mysqli);

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
            }
          }
        ?>
      </table>
    </div>
    <div id="spacer"></div>
    <div id="footer">
      <ul id="footer_menu"><!-- Menu Root -->
        <li class="homeButton"><a href="#"></a></li>
        <li><a href="#">Manual Reply</a>
          <ul class="dropup">
            <li><a href="#">Email</a></li>
            <li><a href="#">SMS</a></li>
            <li><a href="#">Letter</a></li>
          </ul>
        </li>
        <li><a href="#">Auto Reply As...</a>
          <ul class="dropup">
            <li><a href="#">General</a></li>
            <li><a href="#">Bible</a></li>
            <li><a href="#">Competition</a></li>
            <li><a href="#">DVD</a></li>
          </ul>
        </li>
        <li><a href="#">Delegate to...</a>
          <ul class="dropup">
            <li><a href="#">Aziz Masih</a></li>
            <li><a href="#">Preeti James</a></li>
            <li><a href="#">Liza Gill</a></li>
            <li><a href="#">Rachbinder Bunger</a></li>
          </ul>
        </li>
        <li class="right"><a href="#">Logout</a></li>
      </ul>
    </div>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>