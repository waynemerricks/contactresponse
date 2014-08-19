<?php

  include('includes/session.php'); //Include SESSION stuff
  include('includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('includes/loggedIn.php'); //Include logged in check
  include('includes/inbox.php'); //Functions for getting the inbox of this user
  include('includes/user.php'); //Functions relating to logged in user

  $contactsPending = getInbox(getUserId(), isDefaultHelper(), $mysqli);

?>
<html>
  <head>
    <meta charset="utf8">
    <title>CRS | Inbox</title>
    <link rel="stylesheet" type="text/css" href="css/crs.css">
    <script type="text/javascript" src="includes/jquery-2.1.1.min.js"></script>
    <script type="text/javascript">
      //JQuery updateInbox messages
      function updateInbox(){

        $.get( "ajax/test.html", function( data ) {

          $( ".result" ).html( data );
          alert( "Load was performed." );

        });

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

              if($contactsPending[$i]['waiting'] > 99){

                $r = 131;
                $g = 31;
                $b = 1;

              }else if($contactsPending[$i]['waiting'] > 1){

                $r = $r + $contactsPending[$i]['waiting'];
                $g = $g - $contactsPending[$i]['waiting'];
                $b = $b - $contactsPending[$i]['waiting'] / 5;

              }

              $rgb = $r . ', ' . $g . ', ' . round($b);

              if($contactsPending[$i]['waiting'] > 99)
                $contactsPending[$i]['waiting'] = '++';
        ?>
          <tr>
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
            </td>
          </tr>
        <?php
            }
          }
        ?>
      </table>
    </div>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>